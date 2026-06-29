package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.enums.VolumeState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * BacktestIndicatorCalculator 纯函数单测:验证从净值序列+K线逐日派生 MarketIndicators 的各分支。
 */
class BacktestIndicatorCalculatorTest {

    @Test
    void 空净值序列_返回空列表() {
        List<MarketIndicators> result = BacktestIndicatorCalculator.calculate(
                List.of(), List.of(), null);
        assertThat(result).isEmpty();
    }

    @Test
    void 单调上涨_60日新高且价格在年线上方() {
        // 净值从 1.0 每日涨 0.01,共 100 天 → 持续创新高、在均线上方
        List<BigDecimal> nav = ascending(1.0, 0.01, 100);
        List<Instant> dates = dates(100);

        List<MarketIndicators> result = BacktestIndicatorCalculator.calculate(nav, dates, null);

        assertThat(result).hasSize(100);
        MarketIndicators last = result.get(99);
        assertThat(last.sixtyDayHigh()).isTrue();
        assertThat(last.priceAboveYearLine()).isTrue();
        // 年线向上(均值递增)
        assertThat(last.yearLineRising()).isTrue();
        assertThat(last.currentNav()).isCloseTo(new BigDecimal("1.99"), within(new BigDecimal("0.01")));
    }

    @Test
    void 单周跌幅_下跌5期后为正() {
        // 净值 1.0 每日跌 0.01,第 5 天后单周跌幅应 > 0
        List<BigDecimal> nav = descending(1.0, 0.01, 10);
        List<Instant> dates = dates(10);

        List<MarketIndicators> result = BacktestIndicatorCalculator.calculate(nav, dates, null);

        MarketIndicators at5 = result.get(4); // 第5期
        assertThat(at5.weeklyDropPercent()).isNotNull();
        assertThat(at5.weeklyDropPercent()).isPositive();
        // 前4期不足5个交易日,weeklyDropPercent 为 null
        assertThat(result.get(3).weeklyDropPercent()).isNull();
    }

    @Test
    void 量能_K线放量下跌_返回HIGH_DROP() {
        // K线:前19根正常量1000,第20根放量2000且收跌
        List<IndexKline.Bar> bars = new ArrayList<>();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 19; i++) {
            bars.add(new IndexKline.Bar(start.plus(i, ChronoUnit.DAYS),
                    new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("102"),
                    new BigDecimal("99"), 1000L));
        }
        bars.add(new IndexKline.Bar(start.plus(19, ChronoUnit.DAYS),
                new BigDecimal("101"), new BigDecimal("99"), new BigDecimal("102"),
                new BigDecimal("99"), 2000L)); // 放量收跌
        IndexKline kline = new IndexKline(bars);
        List<BigDecimal> nav = ascending(1.0, 0.0, 20);
        List<Instant> dates = dates(20);

        List<MarketIndicators> result = BacktestIndicatorCalculator.calculate(nav, dates, kline);

        // 第20根(索引19)放量下跌 → HIGH_DROP;前19根不足20 → NORMAL
        assertThat(result.get(19).volumeState()).isEqualTo(VolumeState.HIGH_DROP);
        assertThat(result.get(18).volumeState()).isEqualTo(VolumeState.NORMAL);
    }

    @Test
    void 无K线_量能降级NORMAL且不收跌() {
        List<BigDecimal> nav = ascending(1.0, 0.01, 30);
        List<Instant> dates = dates(30);

        List<MarketIndicators> result = BacktestIndicatorCalculator.calculate(nav, dates, null);

        assertThat(result).allMatch(m -> m.volumeState() == VolumeState.NORMAL);
        assertThat(result).allMatch(m -> !m.benchmarkDroppedToday());
    }

    private static List<BigDecimal> ascending(double start, double step, int n) {
        List<BigDecimal> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(BigDecimal.valueOf(start + i * step));
        }
        return list;
    }

    private static List<BigDecimal> descending(double start, double step, int n) {
        List<BigDecimal> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(BigDecimal.valueOf(start - i * step));
        }
        return list;
    }

    private static List<Instant> dates(int n) {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Instant> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(start.plus(i, ChronoUnit.DAYS));
        }
        return list;
    }
}
