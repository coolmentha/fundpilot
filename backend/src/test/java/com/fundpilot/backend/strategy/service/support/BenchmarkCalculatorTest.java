package com.fundpilot.backend.strategy.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #11 循环 B-1:{@link BenchmarkCalculator} 基准线数学 + {@code passed} 判定纯函数。
 */
class BenchmarkCalculatorTest {

    @Test
    void allIn_单调上升_收益正回撤为零() {
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"));

        BenchmarkMetrics metrics = BenchmarkCalculator.allIn(nav);

        // 收益 = 1.2/1.0 - 1 = 0.2;单调上升回撤 0
        assertThat(metrics.returnRate()).isCloseTo(new BigDecimal("0.2"), within(new BigDecimal("0.0001")));
        assertThat(metrics.maxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void allIn_有回撤_收益与回撤正确() {
        // 1.0 → 0.8(回撤 0.2)→ 1.2
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("0.8"), new BigDecimal("1.2"));

        BenchmarkMetrics metrics = BenchmarkCalculator.allIn(nav);

        // 收益 = 1.2/1.0 - 1 = 0.2;回撤 = (1.0-0.8)/1.0 = 0.2
        assertThat(metrics.returnRate()).isCloseTo(new BigDecimal("0.2"), within(new BigDecimal("0.0001")));
        assertThat(metrics.maxDrawdown()).isCloseTo(new BigDecimal("0.2"), within(new BigDecimal("0.0001")));
    }

    @Test
    void dca_两月定投_收益与回撤正确() {
        // 月末扣款:2025-01-31 净值 1.0;2025-02-28 净值 1.2;plannedTotalAmount=1000
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("1.2"));
        List<Instant> dates = List.of(
                Instant.parse("2025-01-31T00:00:00Z"),
                Instant.parse("2025-02-28T00:00:00Z"));

        BenchmarkMetrics metrics = BenchmarkCalculator.dca(nav, dates, new BigDecimal("1000"));

        // 每月 500:day0 买 500 份;day1 买 500/1.2=416.67,合计 916.67 份;期末 916.67*1.2=1100
        // 收益 = (1100-1000)/1000 = 0.1;单调上升回撤 0
        assertThat(metrics.returnRate()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.01")));
        assertThat(metrics.maxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void judgePassed_收益等于沪深300_不通过_要求严格大于() {
        // 策略收益 0.10 == hs300 0.10 → 收益条件不满足(要求 >)
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.10"), new BigDecimal("0.05"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.08")));

        assertThat(passed).isFalse();
    }

    @Test
    void judgePassed_回撤等于allIn_通过_leq允许临界() {
        // 策略回撤 0.10 == allIn 0.10 → 回撤条件满足(<=)
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.08")));

        assertThat(passed).isTrue();
    }

    @Test
    void judgePassed_收益跑赢三条但回撤超allIn_不通过() {
        // 策略收益 0.20 > 三条;但策略回撤 0.15 > allIn 0.10 → 回撤条件不满足
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.15"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.08")));

        assertThat(passed).isFalse();
    }

    @Test
    void judgePassed_收益跑赢三条且回撤不超allIn_通过() {
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.09"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.08")));

        assertThat(passed).isTrue();
    }
}
