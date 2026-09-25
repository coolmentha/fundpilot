package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** 序列的滚动窗口统计与分位；返回序列与入参等长，数据不足窗口的位置为 {@code null}。 */
public final class SeriesStatistics {

    private SeriesStatistics() {
    }

    /** 截至当前位置的窗口内最大值（含当前位置）。 */
    public static List<BigDecimal> trailingMaximum(List<BigDecimal> values, int window) {
        requireWindow(window);
        List<BigDecimal> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            if (index + 1 < window) {
                result.add(null);
                continue;
            }
            BigDecimal maximum = null;
            for (int cursor = index + 1 - window; cursor <= index; cursor++) {
                BigDecimal value = values.get(cursor);
                if (maximum == null || value.compareTo(maximum) > 0) {
                    maximum = value;
                }
            }
            result.add(maximum);
        }
        return result;
    }

    /** 截至当前位置的窗口内最小值（含当前位置）。 */
    public static List<BigDecimal> trailingMinimum(List<BigDecimal> values, int window) {
        requireWindow(window);
        List<BigDecimal> result = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            if (index + 1 < window) {
                result.add(null);
                continue;
            }
            BigDecimal minimum = null;
            for (int cursor = index + 1 - window; cursor <= index; cursor++) {
                BigDecimal value = values.get(cursor);
                if (minimum == null || value.compareTo(minimum) < 0) {
                    minimum = value;
                }
            }
            result.add(minimum);
        }
        return result;
    }

    /** 截至当前位置的窗口内平均值（含当前位置）。 */
    public static List<BigDecimal> trailingAverage(List<BigDecimal> values, int window) {
        requireWindow(window);
        List<BigDecimal> result = new ArrayList<>(values.size());
        BigDecimal divisor = BigDecimal.valueOf(window);
        BigDecimal sum = BigDecimal.ZERO;
        for (int index = 0; index < values.size(); index++) {
            sum = sum.add(values.get(index));
            if (index >= window) {
                sum = sum.subtract(values.get(index - window));
            }
            result.add(index + 1 < window ? null : sum.divide(divisor, MathContext.DECIMAL64));
        }
        return result;
    }

    /** 当前值在样本中的分位：小于等于当前值的样本占比（0~1）。 */
    public static BigDecimal percentileRank(List<BigDecimal> samples, BigDecimal current) {
        if (samples.isEmpty()) {
            throw new IllegalArgumentException("分位样本不能为空");
        }
        if (current == null) {
            throw new IllegalArgumentException("分位当前值不能为空");
        }
        long lower = samples.stream().filter(sample -> sample.compareTo(current) <= 0).count();
        return BigDecimal.valueOf(lower).divide(BigDecimal.valueOf(samples.size()), MathContext.DECIMAL64);
    }

    private static void requireWindow(int window) {
        if (window <= 0) {
            throw new IllegalArgumentException("统计窗口必须为正数");
        }
    }
}