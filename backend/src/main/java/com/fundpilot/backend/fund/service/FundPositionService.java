package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 基金事实仓位服务(issue #9):按份额聚合算「事实仓位」,第一版实时算不缓存
 * (CONTEXT.md §硬性原则:账目只存份额,金额永远实时算)。
 *
 * <h3>核心规则</h3>
 * <ul>
 *   <li>持仓份额 = Σ shares × direction WHERE status = CONFIRMED</li>
 *   <li>在途份额 = Σ shares × direction WHERE status = PENDING</li>
 *   <li>direction:INCREASE/TRANSFER_IN/INVEST = +1,DECREASE/TRANSFER_OUT = -1</li>
 *   <li>CANCELLED 不计入持仓也不计入在途</li>
 * </ul>
 *
 * <p>转账两腿(TRANSFER_IN + TRANSFER_OUT 互指 relatedTransaction)各自独立计入,
 * 因为一腿对本基金是转入(+),另一腿对本基金是转出(-),direction 已天然抵消。
 */
@Service
public class FundPositionService {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundTransactionRepository fundTransactionRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundRepository fundRepository;

    public FundPositionService(FundTransactionRepository fundTransactionRepository,
                               FundNavHistoryRepository fundNavHistoryRepository,
                               FundRepository fundRepository) {
        this.fundTransactionRepository = fundTransactionRepository;
        this.fundNavHistoryRepository = fundNavHistoryRepository;
        this.fundRepository = fundRepository;
    }

    /** CONFIRMED 状态净持仓份额(正=多头,负=超卖)。 */
    public BigDecimal getHoldingShares(Long fundId) {
        return sumShares(fundTransactionRepository.findByFundEntity_IdAndStatus(fundId, FundTransactionStatus.CONFIRMED));
    }

    /** PENDING 状态净在途份额(已下单待净值确认)。 */
    public BigDecimal getPendingShares(Long fundId) {
        return sumShares(fundTransactionRepository.findByFundEntity_IdAndStatus(fundId, FundTransactionStatus.PENDING));
    }

    /**
     * 持仓金额 = 持仓份额 × 当前净值。净值由调用方传入(本期从 market_indicator_snapshot 读)。
     */
    public BigDecimal getHoldingAmount(Long fundId, BigDecimal currentNav) {
        return getHoldingShares(fundId).multiply(currentNav, MATH);
    }

    /**
     * 持仓占比 = 持仓金额 / 总权益持仓金额。
     *
     * @param currentNav  当前净值(用于算持仓金额)
     * @param totalEquity 总权益持仓金额(由调用方汇总所有基金持仓金额得出)
     */
    public BigDecimal getPositionRatio(Long fundId, BigDecimal currentNav, BigDecimal totalEquity) {
        BigDecimal holdingAmount = getHoldingAmount(fundId, currentNav);
        if (totalEquity == null || totalEquity.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return holdingAmount.divide(totalEquity, MATH);
    }

    /**
     * 持仓成本 = Σ amount × direction(CONFIRMED 状态)。名义投入,不算盈亏。
     */
    public BigDecimal getCost(Long fundId) {
        List<FundTransactionEntity> confirmed =
                fundTransactionRepository.findByFundEntity_IdAndStatus(fundId, FundTransactionStatus.CONFIRMED);
        BigDecimal sum = BigDecimal.ZERO;
        for (FundTransactionEntity tx : confirmed) {
            sum = sum.add(tx.getAmount().multiply(direction(tx.getSource())));
        }
        return sum;
    }

    /**
     * 全历史累计净值峰值(ADR-0001:不落字段,实时派生)。
     * 无净值历史时返回 {@link Optional#empty()}。
     */
    public Optional<BigDecimal> getPeakNav(Long fundId) {
        return fundNavHistoryRepository.findPeakAccumulatedNav(fundId);
    }

    /**
     * 持仓期内累计净值峰值:加 {@code navDate >= fund.openedAt} 过滤(ADR-0001)。
     * 无净值历史或 fund.openedAt 为空(返回全历史峰值)时降级。
     */
    public Optional<BigDecimal> getHoldingPeriodPeakNav(Long fundId) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null || fund.getOpenedAt() == null) {
            return getPeakNav(fundId);
        }
        return fundNavHistoryRepository.findPeakAccumulatedNavSince(fundId, fund.getOpenedAt());
    }

    private BigDecimal sumShares(List<FundTransactionEntity> transactions) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FundTransactionEntity tx : transactions) {
            sum = sum.add(tx.getShares().multiply(direction(tx.getSource())));
        }
        return sum;
    }

    /** source → direction 映射:加仓类 +1,减仓类 -1。 */
    private BigDecimal direction(FundTransactionSource source) {
        return switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> BigDecimal.ONE;
            case DECREASE, TRANSFER_OUT -> BigDecimal.ONE.negate();
        };
    }
}
