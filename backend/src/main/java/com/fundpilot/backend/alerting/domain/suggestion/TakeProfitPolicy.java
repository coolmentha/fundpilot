package com.fundpilot.backend.alerting.domain.suggestion;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * 建议型规则的纯计算：卖出份额、浮盈、收益率与回撤。
 *
 * <p>口径与旧 {@code AdvicePolicy} 逐行一致（取 min 公式与浮盈口径照搬），因此迁移前后同一份持仓事实
 * 一定得到同一个建议份额。
 */
public final class TakeProfitPolicy {

    /** 加仓后最少持有交易日数：不足时仍建议卖出，但邮件里给出提示。 */
    public static final int MIN_HOLD_TRADING_DAYS = 5;

    private static final MathContext MATH = MathContext.DECIMAL64;

    private TakeProfitPolicy() {
    }

    /** 持仓成本 = 单位成本 × 确认份额。 */
    public static BigDecimal holdingCost(BigDecimal costPerShare, BigDecimal holdingShares) {
        return costPerShare.multiply(holdingShares, MATH);
    }

    /** 浮盈 = 当前单位净值 × 份额 − 持仓成本，下限为 0。 */
    public static BigDecimal floatingProfit(BigDecimal costPerShare, BigDecimal holdingShares,
                                            BigDecimal currentUnitNav) {
        return currentUnitNav.multiply(holdingShares, MATH)
                .subtract(holdingCost(costPerShare, holdingShares)).max(BigDecimal.ZERO);
    }

    /** 总体收益率 = 浮盈 ÷ 持仓成本；成本非正时无定义。 */
    public static BigDecimal overallReturn(BigDecimal floatingProfit, BigDecimal holdingCost) {
        return positive(holdingCost) ? floatingProfit.divide(holdingCost, MATH) : null;
    }

    /** 峰值回撤 = (周期峰值 − 当前累计净值) ÷ 周期峰值。 */
    public static BigDecimal pullback(BigDecimal peakAccumulatedNav, BigDecimal currentAccumulatedNav) {
        if (!positive(peakAccumulatedNav) || currentAccumulatedNav == null) {
            return null;
        }
        return peakAccumulatedNav.subtract(currentAccumulatedNav).divide(peakAccumulatedNav, MATH);
    }

    /**
     * 建议卖出份额：四者取 min（照搬旧口径）。
     *
     * <p>浮盈 × 收割比例 ÷ 单位净值、份额 × 单次上限、份额 × (1 − 最低保留)、成熟可赎回份额。
     */
    public static BigDecimal suggestedShares(BigDecimal floatingProfit, BigDecimal holdingShares,
                                             BigDecimal currentUnitNav, TakeProfitParams params,
                                             BigDecimal matureRedeemableShares) {
        BigDecimal byProfit = floatingProfit.multiply(params.harvest(), MATH)
                .divide(currentUnitNav, MATH);
        BigDecimal bySingleSell = holdingShares.multiply(params.maxSingleSell(), MATH);
        BigDecimal byMinimumHolding = holdingShares
                .multiply(BigDecimal.ONE.subtract(params.minimumHolding()), MATH);
        BigDecimal shares = byProfit.min(bySingleSell).min(byMinimumHolding).min(matureRedeemableShares);
        return shares.signum() <= 0 ? BigDecimal.ZERO : shares;
    }

    public static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}