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
    void judgePassed_策略Calmar等于dcaCalmar_通过_geq允许临界() {
        // 策略 0.20/0.10=2.0 == dca 0.06/0.03=2.0 → Calmar 临界(>=)过
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.10"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.03")));

        assertThat(passed).isTrue();
    }

    @Test
    void judgePassed_策略Calmar低于dcaCalmar_不通过() {
        // 策略 0.20/0.20=1.0 < dca 0.06/0.03=2.0 → Calmar 输 dca
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.20"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.03")));

        assertThat(passed).isFalse();
    }

    @Test
    void judgePassed_回撤超dca但Calmar仍赢_通过_超额回撤配超额收益() {
        // 策略收益 0.50 > hs300/dca;策略回撤 0.12 > dca 0.08(绝对回撤超),但 Calmar 0.50/0.12=4.17 > dca 0.06/0.08=0.75
        // → 风险调整后更优,通过。绝对回撤约束已弃用,这正是"接受超额回撤但配得上超额收益"
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.50"), new BigDecimal("0.12"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.08")));

        assertThat(passed).isTrue();
    }

    @Test
    void judgePassed_收益输allIn但赢hs300dca_通过() {
        // allIn 收益 0.30 > 策略 0.20,但 allIn 不作收益基准 → 收益条件满足;Calmar 0.20/0.07=2.86 > dca 0.06/0.08=0.75
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.20"), new BigDecimal("0.07"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.30"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.08")));

        assertThat(passed).isTrue();
    }

    @Test
    void judgePassed_dca零回撤策略有回撤_不通过_dca视作无穷Calmar() {
        // dca 回撤 0(单调上涨)→ dcaCalmar 视为 +∞;策略有回撤 → 策略 Calmar 有限值,必输
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.50"), new BigDecimal("0.05"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.00")));

        assertThat(passed).isFalse();
    }

    @Test
    void judgePassed_dca零回撤策略也零回撤_通过_都视作无穷() {
        // dca 回撤 0 + 策略回撤 0 → 双方 Calmar 都 +∞,临界(>=)过(前提收益赢 hs300/dca)
        boolean passed = BenchmarkCalculator.judgePassed(
                new BigDecimal("0.50"), new BigDecimal("0.00"),
                new BenchmarkMetrics(new BigDecimal("0.10"), new BigDecimal("0.20")),
                new BenchmarkMetrics(new BigDecimal("0.08"), new BigDecimal("0.10")),
                new BenchmarkMetrics(new BigDecimal("0.06"), new BigDecimal("0.00")));

        assertThat(passed).isTrue();
    }
}
