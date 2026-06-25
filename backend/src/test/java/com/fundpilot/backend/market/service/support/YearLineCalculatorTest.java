package com.fundpilot.backend.market.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class YearLineCalculatorTest {

    @Test
    void 数据不足_251_点时返回_empty() {
        List<BigDecimal> nav = constantSeries(250, "1.0"); // 仅 250 点,无法对比昨日均线

        Optional<YearLineMetrics> result = YearLineCalculator.calculate(nav);

        assertThat(result).isEmpty();
    }

    @Test
    void 单调上升序列_最新价高于年线且年线向上() {
        // 251 点线性上升 1.00 -> 3.51,最新价显著高于 250 日均线;均线本身也单调上升
        List<BigDecimal> nav = new ArrayList<>();
        for (int i = 0; i < 251; i++) {
            nav.add(new BigDecimal("1.00").add(new BigDecimal("0.01").multiply(BigDecimal.valueOf(i))));
        }

        Optional<YearLineMetrics> result = YearLineCalculator.calculate(nav);

        assertThat(result).isPresent();
        YearLineMetrics m = result.get();
        assertThat(m.priceAboveYearLine()).isTrue();
        assertThat(m.yearLineRising()).isTrue();
        // 今日 250 日均线 = (nav[1] + ... + nav[250]) / 250 = 平均第 125 项 = 1.00 + 0.01 * 125.5 = 2.255
        assertThat(m.yearLineNav()).isCloseTo(new BigDecimal("2.255"), within(new BigDecimal("0.0001")));
    }

    @Test
    void 单调下降序列_最新价低于年线且年线向下() {
        // 251 点线性下降 3.51 -> 1.00,最新价低于均线,均线本身单调下降
        List<BigDecimal> nav = new ArrayList<>();
        for (int i = 0; i < 251; i++) {
            nav.add(new BigDecimal("3.51").subtract(new BigDecimal("0.01").multiply(BigDecimal.valueOf(i))));
        }

        Optional<YearLineMetrics> result = YearLineCalculator.calculate(nav);

        assertThat(result).isPresent();
        assertThat(result.get().priceAboveYearLine()).isFalse();
        assertThat(result.get().yearLineRising()).isFalse();
    }

    @Test
    void null_输入_返回_empty() {
        assertThat(YearLineCalculator.calculate(null)).isEmpty();
    }

    private static List<BigDecimal> constantSeries(int n, String value) {
        List<BigDecimal> series = new ArrayList<>();
        BigDecimal v = new BigDecimal(value);
        for (int i = 0; i < n; i++) {
            series.add(v);
        }
        return series;
    }
}
