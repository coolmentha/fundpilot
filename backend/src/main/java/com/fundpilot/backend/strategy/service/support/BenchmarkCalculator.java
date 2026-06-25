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
 *   <li>策略收益严格大于三条基准:{@code strategyReturn > hs300/allIn/dca}(严格 {@code >},临界不算过)</li>
 *   <li>策略最大回撤 ≤ all-in 最大回撤(用 {@code <=} 避免临界抖动)</li>
 * </ol>
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
     */
    public static boolean judgePassed(BigDecimal strategyReturn, BigDecimal strategyMaxDrawdown,
                                      BenchmarkMetrics hs300, BenchmarkMetrics allInMetrics, BenchmarkMetrics dca) {
        boolean returnBeatsAll = strategyReturn.compareTo(hs300.returnRate()) > 0
                && strategyReturn.compareTo(allInMetrics.returnRate()) > 0
                && strategyReturn.compareTo(dca.returnRate()) > 0;
        boolean drawdownOk = strategyMaxDrawdown.compareTo(allInMetrics.maxDrawdown()) <= 0;
        return returnBeatsAll && drawdownOk;
    }
}
