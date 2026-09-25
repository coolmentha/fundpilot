package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/** 均线计算：给定窗口与序列，返回等长序列，数据不足窗口的位置为 {@code null}。 */
public final class MovingAverage {

    private MovingAverage() {
    }

    /** 简单移动平均；口径与行情快照的年线一致（窗口内取值算术平均）。 */
    public static List<BigDecimal> simple(List<BigDecimal> values, int window) {
        if (window <= 0) {
            throw new IllegalArgumentException("均线窗口必须为正数");
        }
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
}