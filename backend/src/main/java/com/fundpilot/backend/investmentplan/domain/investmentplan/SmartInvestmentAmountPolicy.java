package com.fundpilot.backend.investmentplan.domain.investmentplan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** 固化一版支付宝产品规则的纯金额计算器；不读取行情、不访问数据库。 */
public final class SmartInvestmentAmountPolicy {
    public static final String RULE_VERSION = "ALIPAY_2025_06_V1";
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MIN_AMOUNT = new BigDecimal("0.01");

    public Decision calculate(InvestmentPlanAmountStrategy strategy, BigDecimal baseAmount, Facts facts) {
        Objects.requireNonNull(strategy, "金额策略不能为空");
        requireBaseAmount(baseAmount);
        Objects.requireNonNull(facts, "策略事实不能为空");
        return switch (strategy) {
            case FIXED -> executed(baseAmount, ONE_HUNDRED, "FIXED", null, null);
            case LOW_VALUATION -> lowValuation(baseAmount, facts.valuationPercentile());
            case MOVING_AVERAGE -> movingAverage(baseAmount, facts.indexClose(), facts.movingAverage(),
                    facts.recentAmplitude());
            case CHANGE_RATE -> changeRate(baseAmount, facts.nav(), facts.costPerShare());
        };
    }

    public Decision calculate(String strategy, BigDecimal baseAmount, Facts facts) {
        try {
            return calculate(InvestmentPlanAmountStrategy.valueOf(strategy), baseAmount, facts);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("不支持的金额策略", exception);
        }
    }

    private static Decision lowValuation(BigDecimal baseAmount, BigDecimal percentile) {
        if (percentile == null) return skipped("VALUATION_UNAVAILABLE", "指数估值数据不可用", percentile, null);
        if (percentile.compareTo(BigDecimal.valueOf(30)) <= 0) {
            return executed(baseAmount, ONE_HUNDRED, "LOW_VALUATION", percentile, null);
        }
        return skipped("VALUATION_NOT_LOW", "指数估值未处于低估区", percentile, null);
    }

    private static Decision movingAverage(BigDecimal baseAmount, BigDecimal close,
                                          BigDecimal average, BigDecimal amplitude) {
        if (close == null || average == null || close.signum() <= 0 || average.signum() <= 0) {
            return skipped("INDEX_KLINE_UNAVAILABLE", "指数均线数据不可用", null, amplitude);
        }
        BigDecimal deviation = close.subtract(average).divide(average, 12, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
        BigDecimal rate;
        if (deviation.signum() >= 0) {
            rate = deviation.compareTo(BigDecimal.valueOf(100)) >= 0 ? BigDecimal.valueOf(60)
                    : deviation.compareTo(BigDecimal.valueOf(50)) >= 0 ? BigDecimal.valueOf(70)
                    : deviation.compareTo(BigDecimal.valueOf(15)) >= 0 ? BigDecimal.valueOf(80)
                    : BigDecimal.valueOf(90);
        } else {
            BigDecimal below = deviation.negate();
            int tier = below.compareTo(BigDecimal.valueOf(40)) >= 0 ? 5
                    : below.compareTo(BigDecimal.valueOf(30)) >= 0 ? 4
                    : below.compareTo(BigDecimal.valueOf(20)) >= 0 ? 3
                    : below.compareTo(BigDecimal.valueOf(10)) >= 0 ? 2
                    : below.compareTo(BigDecimal.valueOf(5)) >= 0 ? 1 : 0;
            if (amplitude == null) return skipped("INDEX_KLINE_UNAVAILABLE", "近十日振幅数据不可用", deviation, null);
            rate = amplitude.compareTo(new BigDecimal("0.05")) >= 0
                    ? BigDecimal.valueOf(60 + tier * 10) : BigDecimal.valueOf(160 + tier * 10);
        }
        return executed(baseAmount, rate, "MOVING_AVERAGE", deviation, amplitude);
    }

    private static Decision changeRate(BigDecimal baseAmount, BigDecimal nav, BigDecimal cost) {
        if (nav == null) return skipped("NAV_UNAVAILABLE", "基金最新净值不可用", null, null);
        if (cost == null || cost.signum() <= 0) return skipped("COST_UNAVAILABLE", "平均持仓成本不可用", null, null);
        BigDecimal changeRate = nav.subtract(cost).divide(cost, 12, RoundingMode.HALF_UP);
        BigDecimal rate;
        if (changeRate.compareTo(new BigDecimal("0.25")) >= 0) rate = BigDecimal.valueOf(50);
        else if (changeRate.compareTo(new BigDecimal("0.20")) >= 0) rate = new BigDecimal("52.5");
        else if (changeRate.compareTo(new BigDecimal("0.15")) >= 0) rate = BigDecimal.valueOf(55);
        else if (changeRate.compareTo(new BigDecimal("0.10")) >= 0) rate = BigDecimal.valueOf(60);
        else if (changeRate.compareTo(new BigDecimal("0.075")) >= 0) rate = BigDecimal.valueOf(70);
        else if (changeRate.compareTo(new BigDecimal("0.05")) >= 0) rate = BigDecimal.valueOf(80);
        else if (changeRate.compareTo(new BigDecimal("0.025")) >= 0) rate = BigDecimal.valueOf(90);
        else if (changeRate.compareTo(new BigDecimal("-0.025")) >= 0) rate = ONE_HUNDRED;
        else if (changeRate.compareTo(new BigDecimal("-0.05")) >= 0) rate = BigDecimal.valueOf(120);
        else if (changeRate.compareTo(new BigDecimal("-0.075")) >= 0) rate = BigDecimal.valueOf(140);
        else if (changeRate.compareTo(new BigDecimal("-0.10")) >= 0) rate = BigDecimal.valueOf(160);
        else if (changeRate.compareTo(new BigDecimal("-0.15")) >= 0) rate = BigDecimal.valueOf(180);
        else if (changeRate.compareTo(new BigDecimal("-0.20")) >= 0) rate = BigDecimal.valueOf(190);
        else if (changeRate.compareTo(new BigDecimal("-0.25")) > 0) rate = BigDecimal.valueOf(195);
        else rate = BigDecimal.valueOf(200);
        return executed(baseAmount, rate, "CHANGE_RATE", changeRate, null);
    }

    private static Decision executed(BigDecimal baseAmount, BigDecimal rate, String reason,
                                     BigDecimal primary, BigDecimal secondary) {
        BigDecimal amount = baseAmount.multiply(rate).divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        if (amount.compareTo(MIN_AMOUNT) < 0) amount = MIN_AMOUNT;
        return new Decision(true, amount, rate.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP), null,
                reason, primary, secondary);
    }

    private static Decision skipped(String reasonCode, String reason, BigDecimal primary, BigDecimal secondary) {
        return new Decision(false, null, null, reasonCode, reason, primary, secondary);
    }

    private static void requireBaseAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("基础金额必须大于 0");
    }

    public record Facts(BigDecimal valuationPercentile, BigDecimal indexClose, BigDecimal movingAverage,
                        BigDecimal recentAmplitude, BigDecimal nav, BigDecimal costPerShare) {
        public static Facts empty() { return new Facts(null, null, null, null, null, null); }
    }

    public record Decision(boolean executable, BigDecimal amount, BigDecimal rate, String reasonCode,
                           String reason, BigDecimal primaryMetric, BigDecimal secondaryMetric) {}
}
