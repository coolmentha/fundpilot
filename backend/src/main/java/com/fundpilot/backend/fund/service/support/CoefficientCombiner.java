package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;

/**
 * 调节系数合成器:年线 × MACD × 成交量三维相乘,再 clamp(0.3, 1.5)
 * (CONTEXT.md「调节系数表」)。最终系数 = 基础加仓额 × 合成系数。
 * <p>clamp 用 BigDecimal 比较语义,{@link BigDecimal#min} / {@link BigDecimal#max}
 * 基于 {@code compareTo}(忽略 scale),保证 0.3 与 0.30 等价。
 */
public final class CoefficientCombiner {

    private static final BigDecimal LOWER = new BigDecimal("0.3");
    private static final BigDecimal UPPER = new BigDecimal("1.5");

    private CoefficientCombiner() {
    }

    public static BigDecimal combine(BigDecimal yearLine, BigDecimal macd, BigDecimal volume) {
        BigDecimal product = yearLine.multiply(macd).multiply(volume);
        BigDecimal clampedUp = product.min(UPPER);
        return clampedUp.max(LOWER);
    }
}
