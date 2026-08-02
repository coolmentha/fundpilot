package com.fundpilot.backend.accounting.application.command.transactionledger;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionCreated;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.ledgerreplay.LedgerReplay;
import com.fundpilot.backend.accounting.domain.lot.FifoRedemption;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.ConversionPair;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.ShareScale;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 账目录入用例：手动交易、待确认流水修改、调整交易与目标持仓校准。
 *
 * <p>手动录入绕过信号，买入类写金额、卖出类写份额，另一侧在交易日净值落库后由确认用例回填。
 * 调整交易录入即确认，不计净值与手续费，只改事实份额。
 */
@Service
@RequiredArgsConstructor
public class TransactionLedgerCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionLedgerCommandHandler.class);

    private final TransactionRepository transactions;
    private final LotRepository lots;
    private final TradedPortfolioFundGateway portfolioFunds;
    private final PositionCommandHandler positions;
    private final LedgerEventGateway events;
    private final Clock clock;

    /**
     * 手动录入一笔交易。
     *
     * <p>基金转换：{@code source=TRANSFER_OUT} 且 {@code targetPortfolioFundId} 非空时，创建转出腿与
     * 转入腿并双向互指，返回转出（触发）腿。
     */
    @Transactional
    public LedgerResult recordManual(long ownerId, long portfolioFundId, Source source,
                                     BigDecimal amount, BigDecimal shares, Instant tradeDate,
                                     Long targetPortfolioFundId) {
        return recordManualInternal(ownerId, portfolioFundId,
                source == null ? null : TransactionSource.valueOf(source.name()),
                amount, shares, tradeDate, targetPortfolioFundId);
    }

    @Transactional
    public LedgerResult recordManualForLegacyFund(long ownerId, long legacyFundId, Source source,
                                                   BigDecimal amount, BigDecimal shares, Instant tradeDate,
                                                   Long targetLegacyFundId) {
        long portfolioFundId = requireTradableLegacyFund(ownerId, legacyFundId);
        Long targetPortfolioFundId = targetLegacyFundId == null ? null
                : requireTradableLegacyFund(ownerId, targetLegacyFundId);
        return recordManualInternal(ownerId, portfolioFundId,
                source == null ? null : TransactionSource.valueOf(source.name()),
                amount, shares, tradeDate, targetPortfolioFundId);
    }

    private LedgerResult recordManualInternal(long ownerId, long portfolioFundId, TransactionSource source,
                                              BigDecimal amount, BigDecimal shares, Instant tradeDate,
                                              Long targetPortfolioFundId) {
        if (source == null) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED, "交易来源必填");
        }
        requireTradable(ownerId, portfolioFundId);
        Instant now = clock.instant();
        Instant effectiveTradeDate = tradeDate != null ? tradeDate : now;
        if (effectiveTradeDate.isAfter(now)) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    "交易发生时间不能晚于当前时间");
        }

        if (source.isAdjustment()) {
            return recordAdjustment(ownerId, portfolioFundId, source, shares, effectiveTradeDate, now);
        }
        if (source == TransactionSource.TRANSFER_OUT && targetPortfolioFundId != null) {
            return recordConversion(ownerId, portfolioFundId, targetPortfolioFundId, shares,
                    effectiveTradeDate, now);
        }

        LedgerTransaction transaction = create(() -> LedgerTransaction.placePending(portfolioFundId,
                ownerId, source, amount, shares, effectiveTradeDate, null, null));
        LedgerTransaction saved = transactions.save(transaction);
        publishCreated(saved, now);
        return LedgerResult.from(saved);
    }

    /** 由信号或定投计划生成一笔待确认流水，携带来源幂等键。 */
    @Transactional
    public LedgerResult placePending(long ownerId, long portfolioFundId, TransactionSource source,
                                     BigDecimal amount, BigDecimal shares, Instant tradeDate,
                                     Long signalLogId, Long dcaPlanId) {
        requireTradable(ownerId, portfolioFundId);
        LedgerTransaction transaction = create(() -> LedgerTransaction.placePending(portfolioFundId,
                ownerId, source, amount, shares, tradeDate, signalLogId, dcaPlanId));
        LedgerTransaction saved = transactions.save(transaction);
        publishCreated(saved, clock.instant());
        return LedgerResult.from(saved);
    }

    /** Discipline 建议回应创建待确认账目，建议 ID 是跨模块幂等键，来源原因冗余展示。 */
    @Transactional
    public LedgerResult placePendingForAdvice(long ownerId, long portfolioFundId, Source source,
                                              BigDecimal amount, BigDecimal shares, Instant tradeDate,
                                              long disciplineAdviceId, String signalReason) {
        requireTradable(ownerId, portfolioFundId);
        if (transactions.existsByDisciplineAdviceId(disciplineAdviceId)) {
            throw failure(TransactionLedgerFailure.Code.ADVICE_ALREADY_RESPONDED,
                    "建议 #" + disciplineAdviceId + " 已创建账目");
        }
        LedgerTransaction transaction = create(() -> LedgerTransaction.placePending(portfolioFundId,
                ownerId, TransactionSource.valueOf(source.name()), amount, shares, tradeDate, null, null,
                disciplineAdviceId, signalReason));
        LedgerTransaction saved = transactions.save(transaction);
        publishCreated(saved, clock.instant());
        return LedgerResult.from(saved);
    }

    /** 投资计划按北京业务日幂等创建待确认 INVEST 流水。 */
    @Transactional
    public LedgerResult placePendingForInvestmentPlan(long ownerId, long portfolioFundId,
                                                       BigDecimal amount, Instant tradeDate,
                                                       long investmentPlanId) {
        requireTradable(ownerId, portfolioFundId);
        if (tradeDate == null) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED, "交易发生时间不能为空");
        }
        Instant businessDate = com.fundpilot.backend.sharedkernel.BusinessDay.toDateLabel(tradeDate);
        Instant nextBusinessDate = businessDate.plus(java.time.Duration.ofDays(1));
        if (transactions.existsByInvestmentPlanAndTradeDateBetween(
                investmentPlanId, businessDate, nextBusinessDate)) {
            throw failure(TransactionLedgerFailure.Code.INVESTMENT_PLAN_ALREADY_EXECUTED,
                    "投资计划 #" + investmentPlanId + " 当日已生成账目");
        }
        LedgerTransaction transaction = create(() -> LedgerTransaction.placePending(portfolioFundId,
                ownerId, TransactionSource.INVEST, amount, null, businessDate, null, null, null,
                investmentPlanId));
        LedgerTransaction saved = transactions.save(transaction);
        publishCreated(saved, clock.instant());
        return LedgerResult.from(saved);
    }

    /** 修改一笔待确认流水的业务输入；来源、组合基金与关联关系保持不变。 */
    @Transactional
    public LedgerResult revisePending(long ownerId, long transactionId, BigDecimal amount,
                                      BigDecimal shares, Instant tradeDate) {
        LedgerTransaction transaction = requireOwned(ownerId, transactionId);
        LedgerTransaction related = relatedOf(transaction);
        ConversionPair conversion = ConversionPair.resolve(transaction, related);
        if (conversion != null && conversion.inLeg().id().equals(transaction.id())) {
            throw failure(TransactionLedgerFailure.Code.ILLEGAL_STATE_TRANSITION,
                    "基金转换转入腿由转出腿派生，不可单独修改 #" + transactionId);
        }

        Instant effectiveTradeDate = tradeDate != null ? tradeDate
                : transaction.effectiveTradeDate(clock.instant());
        if (effectiveTradeDate == null || effectiveTradeDate.isAfter(clock.instant())) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    "交易发生时间不能为空或晚于当前时间");
        }

        mutate(() -> transaction.reviseInput(amount, shares, effectiveTradeDate));
        LedgerTransaction saved = transactions.save(transaction);
        if (conversion != null) {
            mutate(() -> conversion.inLeg().reviseTradeDate(effectiveTradeDate));
            transactions.save(conversion.inLeg());
        }
        return LedgerResult.from(saved);
    }

    /** 锁后把事实持仓校准到目标份额；零差额不写流水。 */
    @Transactional
    public TargetHoldingResult adjustToHoldingShares(long ownerId, long portfolioFundId,
                                                     BigDecimal targetShares) {
        requireTradable(ownerId, portfolioFundId);
        BigDecimal target = ShareScale.normalize(targetShares);
        if (target == null || target.signum() < 0) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    "目标持仓份额不能为负数");
        }
        BigDecimal current = ShareScale.normalizeOrZero(LedgerReplay.netShares(
                transactions.findByPortfolioFundAndStatus(portfolioFundId, TransactionStatus.CONFIRMED)));
        BigDecimal difference = target.subtract(current);
        if (difference.signum() == 0) {
            return new TargetHoldingResult(current, target, null);
        }
        TransactionSource source = difference.signum() > 0
                ? TransactionSource.ADJUST_IN : TransactionSource.ADJUST_OUT;
        Instant now = clock.instant();
        LedgerResult adjustment = recordAdjustment(ownerId, portfolioFundId, source,
                difference.abs(), now, now);
        return new TargetHoldingResult(current, target, adjustment);
    }

    private LedgerResult recordAdjustment(long ownerId, long portfolioFundId, TransactionSource source,
                                          BigDecimal shares, Instant tradeDate, Instant now) {
        BigDecimal adjustedShares = ShareScale.normalize(shares);
        if (source == TransactionSource.ADJUST_OUT) {
            BigDecimal holding = LedgerReplay.netShares(transactions.findByPortfolioFundAndStatus(
                    portfolioFundId, TransactionStatus.CONFIRMED));
            if (adjustedShares == null || adjustedShares.compareTo(holding) > 0) {
                throw failure(TransactionLedgerFailure.Code.INSUFFICIENT_HOLDING_SHARES,
                        "调减份额 " + adjustedShares + " 超过当前持仓 " + holding);
            }
        }
        LedgerTransaction transaction = create(() -> LedgerTransaction.recordAdjustment(
                portfolioFundId, ownerId, source, adjustedShares, tradeDate, now));
        LedgerTransaction saved = transactions.save(transaction);

        if (source == TransactionSource.ADJUST_OUT) {
            List<Lot> openLots = lots.findOpenLotsOrderByAcquireDate(portfolioFundId);
            FifoRedemption.Allocation allocation =
                    FifoRedemption.allocateAdjustment(openLots, saved.shares());
            lots.saveAll(allocation.touchedLots());
            if (allocation.unmatchedShares().signum() > 0) {
                log.warn("调减份额超过 open lot portfolio_fund={} shares={} unmatched={}",
                        portfolioFundId, saved.shares(), allocation.unmatchedShares());
            }
        }
        positions.reconcile(portfolioFundId, ownerId);
        publishCreated(saved, now);
        return LedgerResult.from(saved);
    }

    private LedgerResult recordConversion(long ownerId, long portfolioFundId, long targetPortfolioFundId,
                                          BigDecimal shares, Instant tradeDate, Instant now) {
        if (targetPortfolioFundId == portfolioFundId) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    "转入基金不能与转出基金相同");
        }
        requireTradable(ownerId, targetPortfolioFundId);

        LedgerTransaction outLeg = transactions.save(create(() -> LedgerTransaction.placePending(
                portfolioFundId, ownerId, TransactionSource.TRANSFER_OUT, null, shares, tradeDate,
                null, null)));
        LedgerTransaction inLeg = LedgerTransaction.rehydrate(
                pendingInLegPlaceholder(targetPortfolioFundId, ownerId, tradeDate).id(),
                targetPortfolioFundId, ownerId, TransactionSource.TRANSFER_IN, TransactionStatus.PENDING,
                null, null, null, null, null, tradeDate, null, null, now, outLeg.id(), null, null, null,
                null);
        LedgerTransaction savedInLeg = transactions.save(inLeg);

        outLeg.linkRelated(savedInLeg.id());
        LedgerTransaction savedOutLeg = transactions.save(outLeg);
        publishCreated(savedOutLeg, now);
        publishCreated(savedInLeg, now);
        return LedgerResult.from(savedOutLeg);
    }

    /**
     * 转入腿在创建时金额与份额均为空，由转出腿确认后回填，因此不能走
     * {@link LedgerTransaction#placePending} 的输入校验，先落一条占位行取得 ID。
     */
    private LedgerTransaction pendingInLegPlaceholder(long portfolioFundId, long ownerId,
                                                      Instant tradeDate) {
        return transactions.save(LedgerTransaction.placeConversionInLegPlaceholder(
                portfolioFundId, ownerId, tradeDate));
    }

    private void publishCreated(LedgerTransaction saved, Instant occurredAt) {
        events.publishCreated(new TransactionCreated(saved.id(), saved.portfolioFundId(),
                saved.ownerId(), saved.source().name(), saved.amount(), saved.shares(),
                saved.tradeDate(), occurredAt));
    }

    private TradedPortfolioFundGateway.TradedPortfolioFund requireTradable(long ownerId,
                                                                          long portfolioFundId) {
        TradedPortfolioFundGateway.TradedPortfolioFund portfolioFund =
                portfolioFunds.findOwned(ownerId, portfolioFundId)
                        .orElseThrow(() -> failure(
                                TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                                "组合基金不存在: " + portfolioFundId));
        if (!portfolioFund.tradable()) {
            throw failure(TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_TRADABLE,
                    "组合基金已作废，不可记账: " + portfolioFundId);
        }
        return portfolioFund;
    }

    private LedgerTransaction requireOwned(long ownerId, long transactionId) {
        LedgerTransaction transaction = transactions.findByIdForUpdate(transactionId)
                .orElseThrow(() -> failure(TransactionLedgerFailure.Code.TRANSACTION_NOT_FOUND,
                        "交易 #" + transactionId + " 不存在"));
        if (transaction.ownerId() != ownerId) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_NOT_FOUND,
                    "交易 #" + transactionId + " 不存在");
        }
        return transaction;
    }

    private LedgerTransaction relatedOf(LedgerTransaction transaction) {
        return transaction.relatedTransactionId() == null ? null
                : transactions.findById(transaction.relatedTransactionId()).orElse(null);
    }

    /** 把聚合的不变量违规翻译为账目模块的稳定错误码。 */
    private static LedgerTransaction create(java.util.function.Supplier<LedgerTransaction> factory) {
        try {
            return factory.get();
        } catch (IllegalArgumentException exception) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    exception.getMessage());
        }
    }

    private static void mutate(Runnable mutation) {
        try {
            mutation.run();
        } catch (IllegalArgumentException exception) {
            throw failure(TransactionLedgerFailure.Code.TRANSACTION_INPUT_REQUIRED,
                    exception.getMessage());
        } catch (IllegalStateException exception) {
            throw failure(TransactionLedgerFailure.Code.ILLEGAL_STATE_TRANSITION,
                    exception.getMessage());
        }
    }

    private static TransactionLedgerFailure failure(TransactionLedgerFailure.Code code, String message) {
        return new TransactionLedgerFailure(code, message);
    }

    private long requireTradableLegacyFund(long ownerId, long legacyFundId) {
        return portfolioFunds.findByLegacyFundId(legacyFundId)
                .filter(fund -> fund.ownerId() == ownerId && fund.tradable())
                .map(TradedPortfolioFundGateway.TradedPortfolioFund::portfolioFundId)
                .orElseThrow(() -> failure(TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND,
                        "基金不存在: " + legacyFundId));
    }

    public record LedgerResult(long transactionId, long portfolioFundId, long ownerId, String source,
                               String status, BigDecimal amount, BigDecimal shares, BigDecimal nav,
                               BigDecimal fee, BigDecimal feeRate, Instant tradeDate,
                               Instant confirmTime, Instant cancelTime, Instant createdDate, Long relatedTransactionId,
                                Long signalLogId, Long dcaPlanId, Long disciplineAdviceId,
                                Long investmentPlanId) {
        public static LedgerResult from(LedgerTransaction transaction) {
            return new LedgerResult(transaction.id(), transaction.portfolioFundId(),
                    transaction.ownerId(), transaction.source().name(), transaction.status().name(),
                    transaction.amount(), transaction.shares(), transaction.nav(), transaction.fee(),
                    transaction.feeRate(), transaction.tradeDate(), transaction.confirmTime(),
                    transaction.cancelTime(), transaction.createdDate(), transaction.relatedTransactionId(),
                    transaction.signalLogId(), transaction.dcaPlanId(), transaction.disciplineAdviceId(),
                    transaction.investmentPlanId());
        }
    }

    public record TargetHoldingResult(BigDecimal previousShares, BigDecimal targetShares,
                                      LedgerResult transaction) {
    }

    public enum Source {
        INCREASE,
        DECREASE,
        TRANSFER_IN,
        TRANSFER_OUT,
        INVEST,
        ADJUST_IN,
        ADJUST_OUT
    }
}
