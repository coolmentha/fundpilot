package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.common.RequiresNewTransactionExecutor;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import com.fundpilot.backend.strategy.service.TakeProfitLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashSet;

/**
 * 净值确认服务(issue #15):每晚净值公布后回填当天 PENDING 交易的另一侧 + nav + confirmTime,转 CONFIRMED。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>查所有 status=PENDING 的交易</li>
 *   <li>每条:查 fund 当日(UTC 0点起 24 小时区间)NavHistory 行;无则跳过(基金公司未公布净值的边缘情况)</li>
 *   <li>有则:INCREASE→shares=amount/nav;DECREASE→amount=shares×nav;填 nav/confirmTime=now/status=CONFIRMED</li>
 *   <li>转账两腿(TRANSFER_IN/TRANSFER_OUT)按各自方向回填(direction 同 INCREASE/DECREASE)</li>
 * </ol>
 *
 * <h3>交易净值口径</h3>
 * 真实申购、赎回按单位净值 {@code nav} 结算；累计净值只用于复权行情分析。
 *
 * <h3>costPerShare 加权更新(ADR-0013)</h3>
 * INCREASE/TRANSFER_IN/INVEST 确认后同一事务内加权更新 FundEntity.costPerShare。
 */
@Service
@RequiredArgsConstructor
public class NavConfirmService {

    private static final Logger log = LoggerFactory.getLogger(NavConfirmService.class);
    private final FundTransactionRepository fundTransactionRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final TransactionConfirmSupport transactionConfirmSupport;
    private final FundPositionService fundPositionService;
    private final TakeProfitLifecycleService takeProfitLifecycleService;
    private final RequiresNewTransactionExecutor requiresNewTransactionExecutor;

    /**
     * 回填指定 UTC 日期的 PENDING 交易。null 时用今天 UTC 0 点。
     * @return 本次确认的交易条数
     */
    @Transactional
    public int confirmPendingTransactions(Instant date) {
        Instant fallbackDate = date != null ? date : Instant.now();
        List<FundTransactionEntity> pendings = fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING);
        return confirmTransactions(pendings, fallbackDate);
    }

    /** 生产批处理入口：按基金拆分独立事务，单只失败不回滚其他基金。 */
    public int confirmPendingTransactionsIsolated(Instant date) {
        Instant fallbackDate = date != null ? date : Instant.now();
        LinkedHashSet<Long> fundIds = fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING).stream()
                .map(tx -> tx.getFundEntity().getId())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        int confirmed = 0;
        for (Long fundId : fundIds) {
            try {
                confirmed += requiresNewTransactionExecutor.execute(
                        () -> confirmPendingTransactionsForFund(fundId, fallbackDate));
            } catch (RuntimeException ex) {
                log.error("基金待确认交易批量确认失败 fund_id={}: {}", fundId, ex.getMessage(), ex);
            }
        }
        return confirmed;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int confirmPendingTransactionsForFund(Long fundId) {
        return confirmPendingTransactionsForFund(fundId, Instant.now());
    }

    private int confirmPendingTransactionsForFund(Long fundId, Instant fallbackDate) {
        List<FundTransactionEntity> pendings = fundTransactionRepository
                .findByFundEntity_IdAndStatus(fundId, FundTransactionStatus.PENDING);
        return confirmTransactions(pendings, fallbackDate);
    }

    private int confirmTransactions(List<FundTransactionEntity> pendings, Instant fallbackDate) {
        int confirmed = 0;
        for (FundTransactionEntity tx : pendings) {
            Instant dayStart = TransactionTradeDate.resolve(tx, fallbackDate);
            confirmed += tryConfirm(tx, dayStart, dayStart.plus(1, ChronoUnit.DAYS));
        }
        log.info("净值确认完成 fallback_date={} pending={} confirmed={}",
                ChinaTradingDate.toUtcDate(fallbackDate), pendings.size(), confirmed);
        return confirmed;
    }

    /**
     * 尝试确认单条交易;当日无 NavHistory 返回 0 不报错。
     * 基金转换两腿都 PENDING 时要求两只基金同日净值齐备后原子确认；历史半状态只补确认转入腿。
     */
    private int tryConfirm(FundTransactionEntity tx, Instant dayStart, Instant dayEnd) {
        if (tx.getStatus() != FundTransactionStatus.PENDING) {
            return 0;
        }
        ConversionTransactionPair conversion = ConversionTransactionPair.resolve(
                tx, tx.getRelatedFundTransactionEntity());
        if (conversion != null) {
            return tryConfirmConversion(conversion, dayStart, dayEnd);
        }

        BigDecimal navValue = findNavValue(tx.getFundEntity().getId(), dayStart, dayEnd);
        if (navValue == null || !hasRequiredInput(tx)) {
            return 0;
        }
        confirmOne(tx, navValue);
        return 1;
    }

    private int tryConfirmConversion(ConversionTransactionPair conversion,
                                     Instant dayStart,
                                     Instant dayEnd) {
        FundTransactionEntity outLeg = conversion.outLeg();
        FundTransactionEntity inLeg = conversion.inLeg();
        if (outLeg.getStatus() == FundTransactionStatus.CONFIRMED
                && inLeg.getStatus() == FundTransactionStatus.PENDING) {
            BigDecimal inNav = findNavValue(inLeg.getFundEntity().getId(), dayStart, dayEnd);
            if (inNav == null || outLeg.getAmount() == null) {
                return 0;
            }
            inLeg.setAmount(outLeg.getAmount());
            confirmOne(inLeg, inNav);
            return 1;
        }
        if (outLeg.getStatus() != FundTransactionStatus.PENDING
                || inLeg.getStatus() != FundTransactionStatus.PENDING) {
            log.error("基金转换状态异常 out_tx={} out_status={} in_tx={} in_status={}",
                    outLeg.getId(), outLeg.getStatus(), inLeg.getId(), inLeg.getStatus());
            return 0;
        }

        BigDecimal outNav = findNavValue(outLeg.getFundEntity().getId(), dayStart, dayEnd);
        BigDecimal inNav = findNavValue(inLeg.getFundEntity().getId(), dayStart, dayEnd);
        if (outNav == null || inNav == null || !hasRequiredInput(outLeg)) {
            return 0;
        }
        confirmOne(outLeg, outNav);
        inLeg.setAmount(outLeg.getAmount());
        confirmOne(inLeg, inNav);
        return 2;
    }

    private BigDecimal findNavValue(Long fundId, Instant dayStart, Instant dayEnd) {
        FundNavHistoryEntity nav = fundNavHistoryRepository
                .findByFundEntity_IdAndNavDateGreaterThanEqualAndNavDateLessThan(
                        fundId, dayStart, dayEnd).stream()
                .findFirst().orElse(null);
        if (nav == null || nav.getNav() == null || nav.getNav().signum() <= 0) {
            return null;
        }
        return nav.getNav();
    }

    private boolean hasRequiredInput(FundTransactionEntity tx) {
        FundTransactionSource source = tx.getSource();
        return switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                if (tx.getAmount() == null) {
                    log.warn("INCREASE 交易 amount 为空跳过 tx_id={}", tx.getId());
                    yield false;
                }
                yield true;
            }
            case DECREASE, TRANSFER_OUT -> {
                if (tx.getShares() == null) {
                    log.warn("DECREASE 交易 shares 为空跳过 tx_id={}", tx.getId());
                    yield false;
                }
                yield true;
            }
            case ADJUST_IN, ADJUST_OUT -> false;
        };
    }

    private void confirmOne(FundTransactionEntity tx, BigDecimal navValue) {
        if (!hasRequiredInput(tx)) {
            throw new IllegalStateException("确认交易缺少必填字段,tx_id=" + tx.getId());
        }
        FundTransactionSource source = tx.getSource();
        tx.setNav(navValue);
        tx.setConfirmTime(Instant.now());
        // 扣手续费 + 建/消耗 lot + 更新成本单价(统一走 TransactionConfirmSupport)
        switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                tx.setStatus(FundTransactionStatus.CONFIRMED);
                transactionConfirmSupport.onBuyConfirmed(tx, navValue);
            }
            case DECREASE, TRANSFER_OUT -> {
                transactionConfirmSupport.onSellConfirmed(tx, navValue);
                tx.setStatus(FundTransactionStatus.CONFIRMED);
            }
            // ADJUST 不建 lot/不算费(录入即 CONFIRMED,不触达批量确认)
            case ADJUST_IN, ADJUST_OUT -> tx.setStatus(FundTransactionStatus.CONFIRMED);
        }
        fundTransactionRepository.save(tx);
        takeProfitLifecycleService.onTransactionConfirmed(tx);
        fundPositionService.reconcileStatus(tx.getFundEntity().getId());
    }

    // updateCostPerShare 已移至 TransactionConfirmSupport(统一扣费 + lot + 成本更新)
}
