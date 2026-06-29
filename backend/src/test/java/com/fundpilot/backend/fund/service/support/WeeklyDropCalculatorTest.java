package com.fundpilot.backend.fund.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #5 验收:WeeklyDropCalculator 两点跌幅 + 数据不足降级。
 * <p>算法(CONTEXT.md「单周跌幅冷静」):取最近 5 个交易日累计净值(按日期升序,T-5 在前、T-1 在末),
 * 跌幅 = (5天前 - 最近) / 5天前,正数表示下跌幅度。不足 5 个交易日返 empty 降级。
 * 用累计净值 accumulatedNav(分红除权会让单位净值跌幅虚高)。
 */
class WeeklyDropCalculatorTest {

    @Test
    void calculatesTwoPointDropOverFiveTradingDays() {
        // 5 个交易日累计净值,从 1.0000 跌到 0.9500,跌幅 5%
        List<BigDecimal> navHistory = List.of(
                new BigDecimal("1.0000"), new BigDecimal("0.9900"), new BigDecimal("0.9800"),
                new BigDecimal("0.9600"), new BigDecimal("0.9500"));

        BigDecimal drop = WeeklyDropCalculator.calculate(navHistory).orElseThrow();

        // (1.0000 - 0.9500) / 1.0000 = 0.05
        assertThat(drop).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.00000001")));
    }

    @Test
    void returnsEmptyWhenFewerThanFiveTradingDays() {
        List<BigDecimal> navHistory = List.of(
                new BigDecimal("1.0000"), new BigDecimal("0.9900"), new BigDecimal("0.9800"), new BigDecimal("0.9600"));

        assertThat(WeeklyDropCalculator.calculate(navHistory)).isEmpty();
    }

    @Test
    void returnsZeroWhenPriceFlat() {
        List<BigDecimal> navHistory = List.of(
                new BigDecimal("1.0000"), new BigDecimal("1.0000"), new BigDecimal("1.0000"),
                new BigDecimal("1.0000"), new BigDecimal("1.0000"));

        assertThat(WeeklyDropCalculator.calculate(navHistory)).hasValueSatisfying(
                drop -> assertThat(drop).isCloseTo(new BigDecimal("0.00"), within(new BigDecimal("0.00000001"))));
    }

    @Test
    void usesOnlyLastFiveWhenHistoryLonger() {
        // 7 个净值,只取末 5 个:T-5=0.9800 ... T-1=0.9000
        List<BigDecimal> navHistory = List.of(
                new BigDecimal("1.0000"), new BigDecimal("0.9900"), new BigDecimal("0.9800"),
                new BigDecimal("0.9700"), new BigDecimal("0.9600"), new BigDecimal("0.9300"),
                new BigDecimal("0.9000"));

        BigDecimal drop = WeeklyDropCalculator.calculate(navHistory).orElseThrow();

        // 末5个:0.9800, 0.9700, 0.9600, 0.9300, 0.9000 → (0.9800 - 0.9000)/0.9800 ≈ 0.0816
        assertThat(drop).isCloseTo(new BigDecimal("0.0816"), within(new BigDecimal("0.0001")));
    }

    @Test
    void returnsNegativeWhenPriceRising() {
        // 涨了:5天前 0.9500,最近 1.0000 → (0.9500-1.0000)/0.9500 = -0.0526
        List<BigDecimal> navHistory = List.of(
                new BigDecimal("0.9500"), new BigDecimal("0.9600"), new BigDecimal("0.9700"),
                new BigDecimal("0.9800"), new BigDecimal("1.0000"));

        BigDecimal drop = WeeklyDropCalculator.calculate(navHistory).orElseThrow();

        assertThat(drop).isCloseTo(new BigDecimal("-0.0526"), within(new BigDecimal("0.0001")));
    }
}
