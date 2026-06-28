package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.service.support.WeeklyDropCalculator;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.market.service.support.VolumeStateCalculator;
import com.fundpilot.backend.market.service.support.WeeklyMacdCalculator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 回测离线指标计算器(issue #11 重构):从历史净值序列 + 跟踪指数 K 线,
 * 逐日派生 {@link MarketIndicators} 供 {@link DisciplineStrategyService#evaluateSignal} 使用。
 * <p>生产环境的指标由 14:50 行情拉取落 {@code market_indicator_snapshot} 表;回测窗口内
 * 没有历史快照,故从原始净值序列现算。口径对齐生产计算器:
 * <ul>
 *   <li>年线 = 近 250 日累计净值均值(不足用可用长度)</li>
 *   <li>60 日新高 = 当日净值 ≥ 近 60 日最大值</li>
 *   <li>单周跌幅 = 复用 {@link WeeklyDropCalculator}(最近 5 期两点跌幅)</li>
 *   <li>周 MACD = 复用 {@link WeeklyMacdCalculator}(按 ISO 周分组,不足 30 周降级 null)</li>
 *   <li>量能 = 复用 {@link VolumeStateCalculator}(取截至当日的 K 线最近 20 根,不足降级 NORMAL)</li>
 * </ul>
 *
 * <h3>逐日滑窗</h3>
 * 对第 i 个净值日,用 [0, i] 窗口算"截至当日"的指标(信息集为当日及之前,不偷看未来)。
 */
public final class BacktestIndicatorCalculator {

    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final int YEAR_LINE_DAYS = 250;
    private static final int SIXTY_DAY_WINDOW = 60;

    private BacktestIndicatorCalculator() {
    }

    /**
     * 逐日计算回测窗口内的行情指标。
     *
     * @param navSequence    累计净值序列(按日期升序)
     * @param navDates       对应净值日期(升序,与 navSequence 等长)
     * @param benchmarkKline 跟踪指数日 K(含成交量,可为 null——量能类指标降级)
     * @return 与 navSequence 等长的 MarketIndicators 列表
     */
    public static List<MarketIndicators> calculate(
            List<BigDecimal> navSequence, List<Instant> navDates, IndexKline benchmarkKline) {
        if (navSequence == null || navSequence.isEmpty()) {
            return List.of();
        }
        int n = navSequence.size();
        List<MarketIndicators> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(indicatorAt(navSequence, navDates, benchmarkKline, i));
        }
        return List.copyOf(result);
    }

    /** 算第 i 个净值日的指标(窗口 [0, i])。 */
    private static MarketIndicators indicatorAt(
            List<BigDecimal> navSequence, List<Instant> navDates, IndexKline benchmarkKline, int i) {
        BigDecimal currentNav = navSequence.get(i);
        List<BigDecimal> window = navSequence.subList(0, i + 1);

        BigDecimal yearLine = movingAverage(window, YEAR_LINE_DAYS);
        boolean priceAboveYearLine = yearLine.signum() > 0 && currentNav.compareTo(yearLine) >= 0;
        BigDecimal prevYearLine = i > 0 ? movingAverage(navSequence.subList(0, i), YEAR_LINE_DAYS) : BigDecimal.ZERO;
        boolean yearLineRising = yearLine.signum() > 0 && yearLine.compareTo(prevYearLine) > 0;

        boolean sixtyDayHigh = isSixtyDayHigh(window, currentNav);

        Optional<BigDecimal> weeklyDrop = WeeklyDropCalculator.calculate(window);
        // 周 MACD 不足 30 周降级为 GREEN_SHRINKING(系数 1.0 中性),避免 CoefficientTable 查 null NPE
        WeeklyMacdState macdState = WeeklyMacdCalculator.calculate(toSnapshots(window, navDates))
                .orElse(WeeklyMacdState.GREEN_SHRINKING);
        VolumeState volumeState = volumeAt(benchmarkKline, i);
        VolumeState benchmarkVolumeState = volumeState; // 单基金回测复用同一 K 线
        boolean benchmarkDroppedToday = benchmarkDroppedAt(benchmarkKline, i);

        return new MarketIndicators(
                currentNav,
                priceAboveYearLine,
                yearLineRising,
                macdState,
                volumeState,
                weeklyDrop.orElse(null),
                sixtyDayHigh,
                benchmarkVolumeState,
                benchmarkDroppedToday);
    }

    /** 近 window 日均值(不足 window 用可用长度,空窗口返 0)。 */
    private static BigDecimal movingAverage(List<BigDecimal> window, int windowDays) {
        if (window.isEmpty()) {
            return BigDecimal.ZERO;
        }
        int start = Math.max(0, window.size() - windowDays);
        BigDecimal sum = BigDecimal.ZERO;
        for (int j = start; j < window.size(); j++) {
            sum = sum.add(window.get(j), MATH);
        }
        return sum.divide(BigDecimal.valueOf(window.size() - start), MATH);
    }

    /** 当日净值是否为近 60 日最大值(含当日;不足 60 日用可用长度)。 */
    private static boolean isSixtyDayHigh(List<BigDecimal> window, BigDecimal currentNav) {
        if (window.isEmpty()) {
            return false;
        }
        int start = Math.max(0, window.size() - SIXTY_DAY_WINDOW);
        BigDecimal max = window.get(start);
        for (int j = start + 1; j < window.size(); j++) {
            if (window.get(j).compareTo(max) > 0) {
                max = window.get(j);
            }
        }
        return currentNav.compareTo(max) >= 0;
    }

    /** BigDecimal 窗口 + 日期 → FundNavSnapshot 列表(WeeklyMacdCalculator 需要快照格式)。 */
    private static List<FundNavSnapshot> toSnapshots(List<BigDecimal> window, List<Instant> navDates) {
        List<FundNavSnapshot> snapshots = new ArrayList<>(window.size());
        for (int j = 0; j < window.size(); j++) {
            Instant date = j < navDates.size() ? navDates.get(j) : Instant.EPOCH;
            snapshots.add(new FundNavSnapshot(date, window.get(j), window.get(j)));
        }
        return snapshots;
    }

    /** 取截至第 i 个净值日的 K 线滑窗算量能(对齐 VolumeStateCalculator 取最近 20 根)。 */
    private static VolumeState volumeAt(IndexKline kline, int i) {
        if (kline == null || kline.bars() == null || kline.bars().isEmpty()) {
            return VolumeState.NORMAL;
        }
        // K 线与净值日未必等长,按比例映射到第 i 个净值日对应的 K 线位置
        int klineIndex = Math.min(i, kline.bars().size() - 1);
        List<IndexKline.Bar> bars = kline.bars().subList(0, klineIndex + 1);
        return VolumeStateCalculator.calculate(new IndexKline(bars)).orElse(VolumeState.NORMAL);
    }

    /** 第 i 个净值日对应的 K 线当日是否收跌(close < open)。 */
    private static boolean benchmarkDroppedAt(IndexKline kline, int i) {
        if (kline == null || kline.bars() == null || kline.bars().isEmpty()) {
            return false;
        }
        int klineIndex = Math.min(i, kline.bars().size() - 1);
        IndexKline.Bar bar = kline.bars().get(klineIndex);
        return bar.close().compareTo(bar.open()) < 0;
    }
}
