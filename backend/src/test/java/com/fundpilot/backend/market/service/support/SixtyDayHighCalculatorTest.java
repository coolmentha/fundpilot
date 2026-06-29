package com.fundpilot.backend.market.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SixtyDayHighCalculatorTest {

    @Test
    void 数据不足_60_天_返回_empty() {
        List<BigDecimal> nav = constantSeries(59, "1.0");

        assertThat(SixtyDayHighCalculator.calculate(nav)).isEmpty();
    }

    @Test
    void null_输入_返回_empty() {
        assertThat(SixtyDayHighCalculator.calculate(null)).isEmpty();
    }

    @Test
    void 最近_60_天内最高且为末点_返回_true() {
        // 前 100 天稳定 1.0,最近 60 天单调上升,末点即最近 60 天最高
        List<BigDecimal> flat = constantSeries(100, "1.0");
        List<BigDecimal> rising = IntStream.rangeClosed(1, 60)
                .mapToObj(i -> new BigDecimal("1.00").add(BigDecimal.valueOf(i * 0.01)))
                .toList();
        List<BigDecimal> nav = new java.util.ArrayList<>(flat);
        nav.addAll(rising);

        Optional<Boolean> result = SixtyDayHighCalculator.calculate(nav);

        assertThat(result).contains(true);
    }

    @Test
    void 末点低于_60_日内最高_返回_false() {
        // 末点是 1.0,但 60 天窗口内出现过 2.0 → 不是新高
        List<BigDecimal> nav = new java.util.ArrayList<>();
        for (int i = 0; i < 59; i++) {
            nav.add(new BigDecimal("1.0"));
        }
        nav.add(new BigDecimal("2.0")); // 第 60 天创新高
        nav.add(new BigDecimal("1.0"));  // 第 61 天回落,即末点

        Optional<Boolean> result = SixtyDayHighCalculator.calculate(nav);

        assertThat(result).contains(false);
    }

    @Test
    void 末点等于_60_日内最高_返回_true() {
        // 末点 2.0 与窗口内最高相等( plateau ),按新高处理
        List<BigDecimal> nav = new java.util.ArrayList<>();
        for (int i = 0; i < 59; i++) {
            nav.add(new BigDecimal("1.0"));
        }
        nav.add(new BigDecimal("2.0"));
        nav.add(new BigDecimal("2.0"));

        assertThat(SixtyDayHighCalculator.calculate(nav)).contains(true);
    }

    private static List<BigDecimal> constantSeries(int n, String value) {
        return IntStream.range(0, n).mapToObj(i -> new BigDecimal(value)).toList();
    }
}
