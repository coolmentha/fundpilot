package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import java.math.BigDecimal;
import java.util.List;

/** MACD 计算：EMA → DIF → DEA → 柱；口径与行情快照的周线 MACD 一致。 */
public final class Macd {

    private Macd() {
    }

    /** 指数移动平均；首值取序列首点，与既有周线 MACD 口径保持一致。 */
    public static double[] ema(List<BigDecimal> values, int period) {
        if (period <= 0) {
            throw new IllegalArgumentException("EMA 周期必须为正数");
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("EMA 序列不能为空");
        }
        double[] result = new double[values.size()];
        result[0] = values.getFirst().doubleValue();
        double alpha = 2.0 / (period + 1.0);
        for (int index = 1; index < values.size(); index++) {
            result[index] = alpha * values.get(index).doubleValue() + (1 - alpha) * result[index - 1];
        }
        return result;
    }

    /** 计算 DIF、DEA 与柱高；柱高为 {@code 2 × (DIF − DEA)}，与既有口径一致。 */
    public static Line compute(List<BigDecimal> values, int fast, int slow, int signal) {
        if (fast <= 0 || slow <= 0 || signal <= 0) {
            throw new IllegalArgumentException("MACD 周期必须为正数");
        }
        if (fast >= slow) {
            throw new IllegalArgumentException("MACD 快线周期必须小于慢线周期");
        }
        double[] fastEma = ema(values, fast);
        double[] slowEma = ema(values, slow);
        double[] dif = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            dif[index] = fastEma[index] - slowEma[index];
        }
        double[] dea = ema(asValues(dif), signal);
        double[] histogram = new double[values.size()];
        for (int index = 0; index < values.size(); index++) {
            histogram[index] = 2 * (dif[index] - dea[index]);
        }
        return new Line(dif, dea, histogram);
    }

    private static List<BigDecimal> asValues(double[] numbers) {
        return java.util.Arrays.stream(numbers).mapToObj(BigDecimal::valueOf).toList();
    }

    public record Line(double[] dif, double[] dea, double[] histogram) {
    }
}