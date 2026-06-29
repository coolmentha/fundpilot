package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 基准线计算(issue #11):all-in / DCA 两条基准线的收益与最大回撤,以及 {@code passed} 判定纯函数。
 * <p>沪深300 基准线由 {@link Hs300BenchmarkProvider} 从东方财富拉指数序列后,
 * 同样用 {@link MaxDrawdownCalculator} 计算收益与回撤。
 *
 * <h3>passed 判定(issue #11 两条同时满足)</h3>
 * <ol>
 *   <li>策略收益严格大于两条基准:{@code strategyReturn > hs300/dca}(严格 {@code >},临界不算过)。
 *       all-in 不作收益基准——单边涨短窗口不可战胜,且与「风险调整收益」规避激进参数相悖,
 *       用它卡收益会惩罚纪律策略。</li>
 *   <li>策略风险调整收益(Calmar)不劣于 DCA:{@code strategyCalmar >= dcaCalmar}(用 {@code >=} 允许临界)。
 *       Calmar = 收益/最大回撤,语义"单位回撤换的收益"。dca 是分批建仓同语义对照,
 *       策略 Calmar 低于定投说明超额回撤没换来对等的超额收益——择档加仓在制造风险而非补偿风险。
 *       绝对回撤约束(策略回撤 ≤ dca 回撤)已弃用:它惩罚用合理风险换合理收益的策略
 *       (策略收益 50%/回撤 12% vs dca 15%/8% 明显更优却判 false)。</li>
 * </ol>
 *
 * <p>除零兜底:dca 回撤为 0(单调上涨窗口)时 dcaCalmar 视为 +∞(null 表示),策略方有限值必输——
 * 定投零回撤换正收益风险调整后无敌,策略有任何回撤就不如定投。
 */
public final class BenchmarkCalculator {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private BenchmarkCalculator() {
    }

    /**
     * 一次性 all-in:期初按 {@code plannedTotalAmount} 全买,市值正比于净值。
     * <ul>
     *   <li>收益 = lastNav / firstNav - 1</li>
     *   <li>最大回撤 = 净值序列的最大回撤(市值与净值同比例缩放,回撤不变)</li>
     * </ul>
     */
    public static BenchmarkMetrics allIn(List<BigDecimal> navSequence) {
        if (navSequence == null || navSequence.size() < 2) {
            return new BenchmarkMetrics(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal first = navSequence.get(0);
        BigDecimal last = navSequence.get(navSequence.size() - 1);
        BigDecimal returnRate = last.divide(first, MATH).subtract(BigDecimal.ONE);
        BigDecimal maxDrawdown = MaxDrawdownCalculator.calculate(navSequence);
        return new BenchmarkMetrics(returnRate, maxDrawdown);
    }

    /**
     * 等额定投:每月最后交易日扣款,金额 = {@code plannedTotalAmount / 月数}。
     * 逐日累计份额,市值 = 份额 × 当日净值。
     *
     * @param navSequence       累计净值序列(升序)
     * @param navDates          对应净值日期(UTC,长度须与 navSequence 一致)
     * @param plannedTotalAmount 计划总仓位
     */
    public static BenchmarkMetrics dca(List<BigDecimal> navSequence, List<Instant> navDates,
                                       BigDecimal plannedTotalAmount) {
        if (navSequence == null || navSequence.isEmpty()
                || navDates == null || navDates.size() != navSequence.size()
                || plannedTotalAmount == null || plannedTotalAmount.signum() <= 0) {
            return new BenchmarkMetrics(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        // 按年月分组,取每月最后一个交易日的下标(该日扣款)
        LinkedHashMap<YearMonth, Integer> lastDayOfMonth = new LinkedHashMap<>();
        for (int i = 0; i < navDates.size(); i++) {
            YearMonth ym = YearMonth.from(navDates.get(i).atZone(ZoneOffset.UTC));
            lastDayOfMonth.put(ym, i);
        }
        int monthCount = lastDayOfMonth.size();
        BigDecimal perMonthAmount = plannedTotalAmount.divide(BigDecimal.valueOf(monthCount), MATH);
        Set<Integer> dcaDayIndices = new HashSet<>(lastDayOfMonth.values());

        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        List<BigDecimal> dailyValues = new java.util.ArrayList<>(navSequence.size());
        for (int i = 0; i < navSequence.size(); i++) {
            if (dcaDayIndices.contains(i)) {
                shares = shares.add(perMonthAmount.divide(navSequence.get(i), MATH));
                invested = invested.add(perMonthAmount);
            }
            dailyValues.add(shares.multiply(navSequence.get(i), MATH));
        }
        BigDecimal finalValue = shares.multiply(navSequence.get(navSequence.size() - 1), MATH);
        BigDecimal returnRate = invested.signum() > 0
                ? finalValue.subtract(invested).divide(invested, MATH)
                : BigDecimal.ZERO;
        BigDecimal maxDrawdown = MaxDrawdownCalculator.calculate(dailyValues);
        return new BenchmarkMetrics(returnRate, maxDrawdown);
    }

    /**
     * passed 判定(见类注释)。
     *
     * @param allInMetrics all-in 指标;判定已不使用(仅保留参数以维持调用方契约),
     *                     调用方仍计算并落库展示基金自然回撤
     */
    public static boolean judgePassed(BigDecimal strategyReturn, BigDecimal strategyMaxDrawdown,
                                      BenchmarkMetrics hs300, BenchmarkMetrics allInMetrics, BenchmarkMetrics dca) {
        boolean returnBeatsBenchmarks = strategyReturn.compareTo(hs300.returnRate()) > 0
                && strategyReturn.compareTo(dca.returnRate()) > 0;
        // Calmar 比较:null 表示 +∞(零回撤)。dca +∞ 时策略须也 +∞(都零回撤)才临界过;
        // 策略 +∞ 而 dca 有限值时策略赢;双方有限值按数值比(>= 允许临界)
        BigDecimal strategyCalmar = calmarRatio(strategyReturn, strategyMaxDrawdown);
        BigDecimal dcaCalmar = calmarRatio(dca.returnRate(), dca.maxDrawdown());
        boolean calmarOk;
        if (dcaCalmar == null) {
            // dca +∞:策略须也零回撤(+∞)才不劣于,否则必输
            calmarOk = strategyCalmar == null;
        } else if (strategyCalmar == null) {
            // 策略 +∞,dca 有限值:策略赢
            calmarOk = true;
        } else {
            calmarOk = strategyCalmar.compareTo(dcaCalmar) >= 0;
        }
        return returnBeatsBenchmarks && calmarOk;
    }

    /**
     * Calmar 比率 = 收益 / 最大回撤(类 Calmar,与 {@link OptimizeParamRanker} train 排序同源)。
     * 回撤为 0 时返 {@code null} 表示 +∞——零回撤换正收益风险调整后无敌。
     * 收益为 0 或负时仍按公式计算(负 Calmar 有意义:回撤换来的负收益,越接近 0 越好)。
     */
    public static BigDecimal calmarRatio(BigDecimal returnRate, BigDecimal maxDrawdown) {
        if (maxDrawdown == null || maxDrawdown.signum() <= 0) {
            return null;
        }
        return returnRate.divide(maxDrawdown, MATH);
    }
}
