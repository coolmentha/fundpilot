package com.fundpilot.backend.investmentplan.domain.investmentplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SmartInvestmentAmountPolicyTest {
    private final SmartInvestmentAmountPolicy policy = new SmartInvestmentAmountPolicy();
    private final BigDecimal base = new BigDecimal("1000");

    @Test
    void 低估策略仅在百分位三十及以下执行() {
        assertThat(policy.calculate(InvestmentPlanAmountStrategy.LOW_VALUATION, base,
                new SmartInvestmentAmountPolicy.Facts(new BigDecimal("30"), null, null, null, null, null))
                .amount()).isEqualByComparingTo("1000");
        assertThat(policy.calculate(InvestmentPlanAmountStrategy.LOW_VALUATION, base,
                new SmartInvestmentAmountPolicy.Facts(new BigDecimal("30.01"), null, null, null, null, null))
                .executable()).isFalse();
    }

    @Test
    void 均线策略覆盖高于和低于均线的边界() {
        var above = policy.calculate(InvestmentPlanAmountStrategy.MOVING_AVERAGE, base,
                new SmartInvestmentAmountPolicy.Facts(null, new BigDecimal("1.15"), new BigDecimal("1"),
                        new BigDecimal("0.04"), null, null));
        var below = policy.calculate(InvestmentPlanAmountStrategy.MOVING_AVERAGE, base,
                new SmartInvestmentAmountPolicy.Facts(null, new BigDecimal("0.96"), new BigDecimal("1"),
                        new BigDecimal("0.04"), null, null));
        assertThat(above.amount()).isEqualByComparingTo("800");
        assertThat(below.amount()).isEqualByComparingTo("1600");
    }

    @Test
    void 均线策略按偏离档位限制在百分之六十到百分之二百一十() {
        List<BigDecimal> closesAbove = List.of(new BigDecimal("1"), new BigDecimal("1.15"),
                new BigDecimal("1.50"), new BigDecimal("2.00"));
        List<BigDecimal> expectedAbove = List.of(new BigDecimal("0.90"), new BigDecimal("0.80"),
                new BigDecimal("0.70"), new BigDecimal("0.60"));
        for (int index = 0; index < closesAbove.size(); index++) {
            var result = policy.calculate(InvestmentPlanAmountStrategy.MOVING_AVERAGE, base,
                    new SmartInvestmentAmountPolicy.Facts(null, closesAbove.get(index), BigDecimal.ONE,
                            new BigDecimal("0.05"), null, null));
            assertThat(result.rate()).isEqualByComparingTo(expectedAbove.get(index));
        }

        List<BigDecimal> closesBelow = List.of(new BigDecimal("0.95"), new BigDecimal("0.90"),
                new BigDecimal("0.80"), new BigDecimal("0.70"), new BigDecimal("0.60"));
        List<BigDecimal> expectedBelow = List.of(new BigDecimal("0.70"), new BigDecimal("0.80"),
                new BigDecimal("0.90"), new BigDecimal("1.00"), new BigDecimal("1.10"));
        for (int index = 0; index < closesBelow.size(); index++) {
            var result = policy.calculate(InvestmentPlanAmountStrategy.MOVING_AVERAGE, base,
                    new SmartInvestmentAmountPolicy.Facts(null, closesBelow.get(index), BigDecimal.ONE,
                            new BigDecimal("0.05"), null, null));
            assertThat(result.rate()).isEqualByComparingTo(expectedBelow.get(index));
        }

        var calm = policy.calculate(InvestmentPlanAmountStrategy.MOVING_AVERAGE, base,
                new SmartInvestmentAmountPolicy.Facts(null, new BigDecimal("0.60"), BigDecimal.ONE,
                        new BigDecimal("0.0499"), null, null));
        assertThat(calm.rate()).isEqualByComparingTo("2.10");
    }

    @Test
    void 均线策略低于均线但缺少振幅时跳过() {
        var result = policy.calculate(InvestmentPlanAmountStrategy.MOVING_AVERAGE, base,
                new SmartInvestmentAmountPolicy.Facts(null, new BigDecimal("0.95"), BigDecimal.ONE,
                        null, null, null));

        assertThat(result.executable()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("INDEX_KLINE_UNAVAILABLE");
    }

    @Test
    void 涨跌幅策略金额按两位舍入并限制档位() {
        var result = policy.calculate(InvestmentPlanAmountStrategy.CHANGE_RATE, new BigDecimal("99.99"),
                new SmartInvestmentAmountPolicy.Facts(null, null, null, null,
                        new BigDecimal("0.75"), new BigDecimal("1")));
        assertThat(result.amount()).isEqualByComparingTo("199.98");
        assertThat(result.rate()).isEqualByComparingTo("2");
    }

    @Test
    void 涨跌幅策略覆盖正负边界() {
        List<BigDecimal> changes = List.of(new BigDecimal("1.25"), new BigDecimal("1.20"),
                new BigDecimal("1.15"), new BigDecimal("1.025"), new BigDecimal("1"),
                new BigDecimal("0.975"), new BigDecimal("0.95"), new BigDecimal("0.80"),
                new BigDecimal("0.75"));
        List<BigDecimal> rates = List.of(new BigDecimal("0.50"), new BigDecimal("0.525"),
                new BigDecimal("0.55"), new BigDecimal("0.90"), new BigDecimal("1.00"),
                new BigDecimal("1.00"), new BigDecimal("1.20"), new BigDecimal("1.90"),
                new BigDecimal("2.00"));
        for (int index = 0; index < changes.size(); index++) {
            var result = policy.calculate(InvestmentPlanAmountStrategy.CHANGE_RATE, base,
                    new SmartInvestmentAmountPolicy.Facts(null, null, null, null,
                            changes.get(index), BigDecimal.ONE));
            assertThat(result.rate()).isEqualByComparingTo(rates.get(index));
        }
    }

    @Test
    void 缺少智能输入只能跳过() {
        var result = policy.calculate(InvestmentPlanAmountStrategy.CHANGE_RATE, base,
                SmartInvestmentAmountPolicy.Facts.empty());
        assertThat(result.executable()).isFalse();
        assertThat(result.reasonCode()).isEqualTo("NAV_UNAVAILABLE");
    }
}
