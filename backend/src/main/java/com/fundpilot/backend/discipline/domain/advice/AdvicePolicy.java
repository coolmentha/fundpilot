package com.fundpilot.backend.discipline.domain.advice;

import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** 卖出纪律纯计算策略。 */
public final class AdvicePolicy {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final int MIN_HOLD_DAYS = 5;

    /** Accounting 持仓状态(PositionApi.Status 同名)。 */
    public enum PositionStatus { EMPTY, OPEN, CLEARED }
    /** 产品类型(FundProductApi.ProductType 同名)。 */
    public enum ProductType { ETF, INDEX, INDEX_ENHANCED, ACTIVE }
    /** 周线 MACD 状态(marketdata WeeklyMacdState 同名)。 */
    public enum MacdState { RED_EXPANDING, RED_SHRINKING, GREEN_EXPANDING, GREEN_SHRINKING }
    /** 指数量能状态(marketdata VolumeState 同名)。 */
    public enum VolumeState { HIGH_DROP, NORMAL, LOW_STABLE }

    public Result evaluate(DisciplineStrategy strategy, Facts facts, long tradingDaysSinceLastBuy,
                           boolean cooldownFinished) {
        if (facts.positionStatus() == PositionStatus.CLEARED) {
            return Result.none("FUND_CLEARED");
        }
        if (facts.positionStatus() != PositionStatus.OPEN) {
            return Result.none("NO_STRATEGY");
        }
        if (facts.market() == null) {
            return Result.none("INSUFFICIENT_MARKET_DATA");
        }
        if (logicBroken(facts)) {
            List<String> warnings = tradingDaysSinceLastBuy < MIN_HOLD_DAYS
                    ? List.of("MIN_HOLD_DAYS_OVERRIDDEN") : List.of();
            return new Result(AdviceAction.SELL, facts.holdingShares(), "SHARE", "LOGIC_BROKEN", warnings);
        }
        if (!positive(facts.currentUnitNav()) || !positive(facts.currentAccumulatedNav())
                || !positive(facts.holdingShares()) || !positive(facts.costPerShare())) {
            return Result.none("NO_SELL_TRIGGER");
        }
        BigDecimal holdingCost = facts.costPerShare().multiply(facts.holdingShares(), MATH);
        BigDecimal floatingProfit = facts.currentUnitNav().multiply(facts.holdingShares(), MATH)
                .subtract(holdingCost).max(BigDecimal.ZERO);
        BigDecimal overallReturn = floatingProfit.divide(holdingCost, MATH);
        if (!strategy.prepareTakeProfit(overallReturn, facts.currentAccumulatedNav(), facts.businessDate(),
                cooldownFinished)) {
            return Result.none("NO_SELL_TRIGGER");
        }
        BigDecimal peak = strategy.cyclePeakNav();
        if (!positive(peak)) {
            return Result.none("NO_SELL_TRIGGER");
        }
        BigDecimal pullback = peak.subtract(facts.currentAccumulatedNav()).divide(peak, MATH);
        if (pullback.compareTo(strategy.pullback()) < 0) {
            return Result.none("NO_SELL_TRIGGER");
        }
        BigDecimal shares = min(
                floatingProfit.multiply(strategy.harvest(), MATH).divide(facts.currentUnitNav(), MATH),
                facts.holdingShares().multiply(strategy.maxSingleSell(), MATH),
                facts.holdingShares().multiply(BigDecimal.ONE.subtract(strategy.minimumHolding()), MATH),
                facts.matureRedeemableShares());
        return shares.signum() <= 0 ? Result.none("NO_SELL_TRIGGER")
                : new Result(AdviceAction.SELL, shares, "SHARE", "TRAILING_STOP", List.of());
    }

    private static boolean logicBroken(Facts facts) {
        Boolean priceAboveYearLine = facts.market().priceAboveYearLine();
        if (priceAboveYearLine == null || priceAboveYearLine
                || facts.market().weeklyMacdState() != MacdState.GREEN_EXPANDING) {
            return false;
        }
        return facts.productType() == ProductType.ACTIVE
                || facts.market().volumeState() == VolumeState.HIGH_DROP;
    }

    private static BigDecimal min(BigDecimal first, BigDecimal... values) {
        BigDecimal result = first;
        for (BigDecimal value : values) {
            result = result.min(value);
        }
        return result;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public record Facts(ProductType productType, PositionStatus positionStatus, BigDecimal costPerShare,
                        BigDecimal holdingShares, Market market, BigDecimal currentUnitNav,
                        BigDecimal currentAccumulatedNav, BigDecimal matureRedeemableShares,
                        java.time.Instant businessDate) {
    }

    public record Market(Boolean priceAboveYearLine, MacdState weeklyMacdState, VolumeState volumeState) {
    }

    public record Result(AdviceAction action, BigDecimal suggestedValue, String suggestedMeasureUnit,
                         String reason, List<String> warnings) {
        public Result {
            warnings = List.copyOf(new ArrayList<>(warnings));
        }

        static Result none(String reason) {
            return new Result(AdviceAction.NONE, null, null, reason, List.of());
        }
    }
}
