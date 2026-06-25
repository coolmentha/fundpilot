package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 最大回撤计算器(issue #11):维护 peakSoFar,逐日 (peakSoFar - currentValue) / peakSoFar 取最大值。
 * 用于策略收益序列和三条基准线的回撤计算。
 * <p>序列前导 0(策略建仓前空仓 / DCA 定投前空仓)不构成有效峰值,从首个正值起算,避免除零。
 */
public final class MaxDrawdownCalculator {

    private MaxDrawdownCalculator() {
    }

    /**
     * @param dailyValues 日市值序列(按日期升序)
     * @return 最大回撤比例(正数,0 表示无回撤);空序列或单点返回 0
     */
    public static BigDecimal calculate(List<BigDecimal> dailyValues) {
        if (dailyValues == null || dailyValues.size() < 2) {
            return BigDecimal.ZERO;
        }
        BigDecimal peakSoFar = null; // 跳过序列前导 0(建仓前空仓),首个正值起算回撤
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        for (BigDecimal v : dailyValues) {
            if (peakSoFar == null) {
                if (v.signum() > 0) {
                    peakSoFar = v;
                }
                continue;
            }
            if (v.compareTo(peakSoFar) > 0) {
                peakSoFar = v;
            }
            // peakSoFar 此处必 > 0(由上面赋值/更新保证),不会除零
            BigDecimal drawdown = peakSoFar.subtract(v)
                    .divide(peakSoFar, java.math.MathContext.DECIMAL64);
            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        return maxDrawdown;
    }
}
