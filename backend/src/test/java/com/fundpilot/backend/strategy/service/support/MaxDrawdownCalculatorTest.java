package com.fundpilot.backend.strategy.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MaxDrawdownCalculatorTest {

    @Test
    void 单调上升_回撤为_0() {
        List<BigDecimal> values = List.of(new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("120"));

        BigDecimal dd = MaxDrawdownCalculator.calculate(values);

        assertThat(dd).isCloseTo(BigDecimal.ZERO, within(new BigDecimal("0.0001")));
    }

    @Test
    void 峰值后回落_取最大回撤() {
        // 100 → 110(peak)→ 90 → 95;最大回撤在 90:(110-90)/110 = 0.1818
        List<BigDecimal> values = List.of(
                new BigDecimal("100"), new BigDecimal("110"), new BigDecimal("90"), new BigDecimal("95"));

        BigDecimal dd = MaxDrawdownCalculator.calculate(values);

        assertThat(dd).isCloseTo(new BigDecimal("0.1818"), within(new BigDecimal("0.001")));
    }

    @Test
    void 单调下降_回撤等于首末跌幅() {
        // 100 → 80;回撤 (100-80)/100 = 0.2
        List<BigDecimal> values = List.of(new BigDecimal("100"), new BigDecimal("80"));

        BigDecimal dd = MaxDrawdownCalculator.calculate(values);

        assertThat(dd).isCloseTo(new BigDecimal("0.2"), within(new BigDecimal("0.001")));
    }

    @Test
    void 空序列或单点_返回_0() {
        assertThat(MaxDrawdownCalculator.calculate(List.of())).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(MaxDrawdownCalculator.calculate(List.of(new BigDecimal("100")))).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
