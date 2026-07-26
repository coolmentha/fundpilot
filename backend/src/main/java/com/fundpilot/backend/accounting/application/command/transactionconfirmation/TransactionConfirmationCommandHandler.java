package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCancelled;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementFeeGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementNavGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.ConversionPair;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 交易确认与撤销用例。
 *
 * <p>确认固化交易发生日的单位净值快照，禁止用次日或最新一期净值替代历史成交净值。
 * 基金转换的两条腿必须在两只基金同日净值齐备后原子确认；转入腿的金额由转出腿净额回填。
 */
@Service
@RequiredArgsConstructor
public class TransactionConfirmationCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionConfirmationCommandHandler.class);

    private final TransactionRepository transactions;
    private final LotRepository lots;
    private final TradedPortfolioFundGateway portfolioFunds;
    private final SettlementFeeGateway fees;
    private final SettlementNavGateway navs;
    private final PositionCommandHandler positions;
    private final LedgerEventGateway events;
    private final Clock clock;

    /** 手动确认一笔流水；转换交易两条腿联动确认。返回本次确认的流水 ID。 */
    @Transactional
    public List<Long> confirm(long ownerId, long transactionId) {
        LedgerTransaction transaction = requireOwned(ownerId, transactionId);
        requirePending(transaction);

        List<Long> confirmed = new ArrayList<>();
        ConversionPair conversion = resolveConversion(transaction);
        if (conversion != null) {
            confirmConversion(conversion, confirmed);
        } else {
            confirmOne(transaction, requiredNav(transaction), confirmed);
        }
        log.info("手动确认完成 tx_id={} confirmed={}", transactionId, confirmed.size());
        return confirmed;
    }

    /** 撤销流水；转换交易两条腿一起撤销。返回本次撤销的流水 ID。 */
    @Transactional
    public List<Long> cancel(long ownerId, long transactionId) {
        LedgerTransaction transaction = requireOwned(ownerId, transactionId);
        requirePending(transaction);

        LedgerTransaction related = relatedOf(transaction);
        ConversionPair conversion = ConversionPair.resolve(transaction, related);
        if (conversion != null && related.status() == TransactionStatus.CONFIRMED) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CONFIRMED,
                    "基金转换关联腿已确认，不可单独撤销 #" + transactionId);
        }
        if (conversion != null && related.status() == TransactionStatus.CANCELLED) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CANCELLED,
                    "基金转换关联腿已撤销 #" + transactionId);
        }

        List<LedgerTransaction> cancelled = new ArrayList<>();
        cancelOne(transaction, cancelled);
        if (related != null && related.status() == TransactionStatus.PENDING) {
            cancelOne(related, cancelled);
        }
        cancelled.stream().map(LedgerTransaction::portfolioFundId).distinct()
                .forEach(portfolioFundId -> positions.reconcile(portfolioFundId, ownerOf(portfolioFundId)));

        log.info("撤单完成 tx_id={} cancelled={}", transactionId, cancelled.size());
        return cancelled.stream().map(LedgerTransaction::id).toList();
    }

    /** 净值公布后批量确认某组合基金的 PENDING 流水；当日无净值时静默跳过。 */
    @Transactional
    public int confirmPendingFor(long portfolioFundId, Instant fallbackDate) {
        List<LedgerTransaction> pendings =
                transactions.findByPortfolioFundAndStatus(portfolioFundId, TransactionStatus.PENDING);
        return confirmWhereNavAvailable(pendings, fallbackDate);
    }

    /** 返回仍有 PENDING 流水的组合基金 ID，供调度入口按基金拆分独立事务。 */
    @Transactional(readOnly = true)
    public List<Long> portfolioFundsWithPendingTransactions() {
        return transactions.findByStatus(TransactionStatus.PENDING).stream()
                .map(LedgerTransaction::portfolioFundId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                .stream().toList();
    }

    private int confirmWhereNavAvailable(List<LedgerTransaction> pendings, Instant fallbackDate) {
        int confirmed = 0;
        for (LedgerTransaction transaction : pendings) {
            if (transaction.status() != TransactionStatus.PENDING) {
                continue;
            }
            Instant dayLabel = BusinessDay.toDateLabel(transaction.effectiveTradeDate(fallbackDate));
            ConversionPair conversion = resolveConversion(transaction);
            List<Long> results = new ArrayList<>();
            if (conversion != null) {
                confirmed += tryConfirmConversion(conversion, dayLabel, results);
                continue;
            }
            Optional<BigDecimal> nav = navOn(transaction, dayLabel);
            if (nav.isEmpty() || !transaction.hasRequiredInput()) {
                continue;
            }
            confirmOne(transaction, nav.get(), results);
            confirmed += results.size();
        }
        return confirmed;
    }

    private int tryConfirmConversion(ConversionPair conversion, Instant dayLabel, List<Long> confirmed) {
        LedgerTransaction outLeg = conversion.outLeg();
        LedgerTransaction inLeg = conversion.inLeg();

        if (outLeg.status() == TransactionStatus.CONFIRMED
                && inLeg.status() == TransactionStatus.PENDING) {
            Optional<BigDecimal> inNav = navOn(inLeg, dayLabel);
            if (inNav.isEmpty() || outLeg.amount() == null) {
                return 0;
            }
            inLeg.inheritConversionAmount(outLeg.amount());
            confirmOne(inLeg, inNav.get(), confirmed);
            return 1;
        }
        if (outLeg.status() != TransactionStatus.PENDING || inLeg.status() != TransactionStatus.PENDING) {
            log.error("基金转换状态异常 out_tx={} out_status={} in_tx={} in_status={}",
                    outLeg.id(), outLeg.status(), inLeg.id(), inLeg.status());
            return 0;
        }

        Optional<BigDecimal> outNav = navOn(outLeg, dayLabel);
        Optional<BigDecimal> inNav = navOn(inLeg, dayLabel);
        if (outNav.isEmpty() || inNav.isEmpty() || !outLeg.hasRequiredInput()) {
            return 0;
        }
        confirmOne(outLeg, outNav.get(), confirmed);
        inLeg.inheritConversionAmount(outLeg.amount());
        confirmOne(inLeg, inNav.get(), confirmed);
        return 2;
    }

    private void confirmConversion(ConversionPair conversion, List<Long> confirmed) {
        LedgerTransaction outLeg = conversion.outLeg();
        LedgerTransaction inLeg = conversion.inLeg();
        if (outLeg.status() == TransactionStatus.CANCELLED
                || inLeg.status() == TransactionStatus.CANCELLED) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CANCELLED,
                    "基金转换存在已撤销关联腿，不可确认");
        }
        if (outLeg.status() == TransactionStatus.PENDING
                && inLeg.status() == TransactionStatus.CONFIRMED) {
            throw failure(TransactionConfirmationFailure.Code.ILLEGAL_STATE_TRANSITION,
                    "基金转换转入腿已确认但转出腿仍待确认");
        }
        if (outLeg.status() == TransactionStatus.PENDING) {
            confirmOne(outLeg, requiredNav(outLeg), confirmed);
        }
        if (inLeg.status() == TransactionStatus.PENDING) {
            if (outLeg.status() != TransactionStatus.CONFIRMED || outLeg.amount() == null) {
                throw failure(TransactionConfirmationFailure.Code.ILLEGAL_STATE_TRANSITION,
                        "基金转换转出腿尚未完成，不可确认转入腿");
            }
            inLeg.inheritConversionAmount(outLeg.amount());
            confirmOne(inLeg, requiredNav(inLeg), confirmed);
        }
    }

    private void confirmOne(LedgerTransaction transaction, BigDecimal navValue, List<Long> confirmed) {
        if (!transaction.hasRequiredInput()) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    "确认交易缺少必填字段，tx_id=" + transaction.id());
        }
        TradedPortfolioFundGateway.TradedPortfolioFund portfolioFund = requireTradable(transaction);
        FeeSchedule feeSchedule = fees.feeScheduleOf(portfolioFund.fundProductId());
        Instant now = clock.instant();

        if (transaction.source().isBuy()) {
            TransactionSettlement.Purchase purchase =
                    TransactionSettlement.settleBuy(transaction, navValue, feeSchedule);
            transaction.confirm(purchase.settlement(), now);
            LedgerTransaction saved = transactions.save(transaction);
            lots.save(Lot.open(saved.portfolioFundId(), saved.id(),
                    saved.effectiveTradeDate(now), purchase.shares(), purchase.acquireCostPerShare()));
            positions.applyPurchase(saved.portfolioFundId(), saved.ownerId(), purchase.shares(),
                    purchase.effectiveAmount());
            publishConfirmed(saved, now);
            confirmed.add(saved.id());
            log.info("买入确认扣费 portfolio_fund={} amount={} fee={} shares={} nav={}",
                    saved.portfolioFundId(), saved.amount(), saved.fee(), saved.shares(), navValue);
            return;
        }

        Instant sellTradeTime = transaction.effectiveTradeDate(now);
        guardSellShares(transaction);
        List<Lot> openLots = lots.findOpenLotsOrderByAcquireDate(transaction.portfolioFundId());
        guardLotCoverage(transaction, openLots);

        TransactionSettlement.Sale sale =
                TransactionSettlement.settleSell(transaction, navValue, feeSchedule, openLots, sellTradeTime);
        transaction.confirm(sale.settlement(), now);
        LedgerTransaction saved = transactions.save(transaction);
        lots.saveAll(sale.allocation().touchedLots());
        lots.saveRedemptions(sale.allocation().redemptions());
        positions.reconcile(saved.portfolioFundId(), saved.ownerId());
        publishConfirmed(saved, now);
        confirmed.add(saved.id());
        if (sale.allocation().unmatchedShares().signum() > 0) {
            log.warn("卖出包含未跟踪调整份额 portfolio_fund={} shares={} untracked={}，按零赎回费处理",
                    saved.portfolioFundId(), saved.shares(), sale.allocation().unmatchedShares());
        }
        log.info("卖出确认FIFO portfolio_fund={} shares={} nav={} fee={} net={} lots={}",
                saved.portfolioFundId(), saved.shares(), navValue, saved.fee(), saved.amount(),
                sale.allocation().redemptions().size());
    }

    private void cancelOne(LedgerTransaction transaction, List<LedgerTransaction> cancelled) {
        Instant now = clock.instant();
        transaction.cancel(now);
        LedgerTransaction saved = transactions.save(transaction);
        events.publishCancelled(new TransactionCancelled(saved.id(), saved.portfolioFundId(),
                saved.ownerId(), saved.source().name(), saved.signalLogId(), saved.dcaPlanId(),
                now, saved.id(), now));
        cancelled.add(saved);
    }

    private void publishConfirmed(LedgerTransaction saved, Instant occurredAt) {
        events.publishConfirmed(new TransactionConfirmed(saved.id(), saved.portfolioFundId(),
                saved.ownerId(), saved.source().name(), saved.amount(), saved.shares(), saved.nav(),
                saved.fee(), saved.tradeDate(), saved.confirmTime(), saved.signalLogId(),
                saved.dcaPlanId(), saved.id(), occurredAt));
    }

    /** 卖出份额不得超过 CONFIRMED 事实持仓。 */
    private void guardSellShares(LedgerTransaction transaction) {
        BigDecimal holding = LedgerReplay.netShares(transactions.findByPortfolioFundAndStatus(
                transaction.portfolioFundId(), TransactionStatus.CONFIRMED));
        if (transaction.shares().compareTo(holding) > 0) {
            throw failure(TransactionConfirmationFailure.Code.INSUFFICIENT_HOLDING_SHARES,
                    "卖出份额 " + transaction.shares() + " 超过 CONFIRMED 事实持仓 " + holding);
        }
    }

    /** lot 未覆盖的部分只能来自历史调增产生的合法未跟踪份额。 */
    private void guardLotCoverage(LedgerTransaction transaction, List<Lot> openLots) {
        BigDecimal trackedShares = openLots.stream().map(Lot::remainingShares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal requiredUntracked = transaction.shares().subtract(trackedShares).max(BigDecimal.ZERO);
        BigDecimal availableUntracked = LedgerReplay.untrackedShares(
                transactions.findByPortfolioFundAndStatus(transaction.portfolioFundId(),
                        TransactionStatus.CONFIRMED));
        if (requiredUntracked.compareTo(availableUntracked) > 0) {
            throw failure(TransactionConfirmationFailure.Code.INSUFFICIENT_LOTS,
                    "可用 lot 份额不足，无法卖出 " + transaction.shares() + " 份，缺 "
                            + requiredUntracked + " 份，合法未跟踪份额 " + availableUntracked);
        }
    }

    private ConversionPair resolveConversion(LedgerTransaction transaction) {
        return ConversionPair.resolve(transaction, relatedOf(transaction));
    }

    private LedgerTransaction relatedOf(LedgerTransaction transaction) {
        return transaction.relatedTransactionId() == null ? null
                : transactions.findById(transaction.relatedTransactionId()).orElse(null);
    }

    private BigDecimal requiredNav(LedgerTransaction transaction) {
        Instant dayLabel = BusinessDay.toDateLabel(transaction.effectiveTradeDate(clock.instant()));
        return navOn(transaction, dayLabel).orElseThrow(() -> failure(
                TransactionConfirmationFailure.Code.NAV_UNAVAILABLE,
                "组合基金 #" + transaction.portfolioFundId() + " 缺少交易日 " + dayLabel + " 的净值"));
    }

    private Optional<BigDecimal> navOn(LedgerTransaction transaction, Instant dayLabel) {
        return navs.unitNavOn(requireTradable(transaction).fundProductId(), dayLabel)
                .filter(nav -> nav.signum() > 0);
    }

    private TradedPortfolioFundGateway.TradedPortfolioFund requireTradable(LedgerTransaction transaction) {
        TradedPortfolioFundGateway.TradedPortfolioFund portfolioFund =
                portfolioFunds.find(transaction.portfolioFundId())
                        .orElseThrow(() -> failure(
                                TransactionConfirmationFailure.Code.PORTFOLIO_FUND_NOT_TRADABLE,
                                "组合基金不存在: " + transaction.portfolioFundId()));
        if (!portfolioFund.tradable()) {
            throw failure(TransactionConfirmationFailure.Code.PORTFOLIO_FUND_NOT_TRADABLE,
                    "组合基金已作废，不可记账: " + transaction.portfolioFundId());
        }
        return portfolioFund;
    }

    private LedgerTransaction requireOwned(long ownerId, long transactionId) {
        LedgerTransaction transaction = transactions.findByIdForUpdate(transactionId)
                .orElseThrow(() -> failure(TransactionConfirmationFailure.Code.TRANSACTION_NOT_FOUND,
                        "交易 #" + transactionId + " 不存在"));
        if (transaction.ownerId() != ownerId) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_NOT_FOUND,
                    "交易 #" + transactionId + " 不存在");
        }
        return transaction;
    }

    private void requirePending(LedgerTransaction transaction) {
        if (transaction.status() == TransactionStatus.CONFIRMED) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CONFIRMED,
                    "已确认交易不可再操作 #" + transaction.id());
        }
        if (transaction.status() == TransactionStatus.CANCELLED) {
            throw failure(TransactionConfirmationFailure.Code.TRANSACTION_ALREADY_CANCELLED,
                    "已撤销交易不可再操作 #" + transaction.id());
        }
    }

    private long ownerOf(long portfolioFundId) {
        return portfolioFunds.find(portfolioFundId)
                .map(TradedPortfolioFundGateway.TradedPortfolioFund::ownerId)
                .orElseThrow(() -> failure(
                        TransactionConfirmationFailure.Code.PORTFOLIO_FUND_NOT_TRADABLE,
                        "组合基金不存在: " + portfolioFundId));
    }

    private static TransactionConfirmationFailure failure(TransactionConfirmationFailure.Code code,
                                                          String message) {
        return new TransactionConfirmationFailure(code, message);
    }
}
