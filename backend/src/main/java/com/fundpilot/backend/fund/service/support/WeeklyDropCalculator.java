package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * 单周跌幅计算器(CONTEXT.md「单周跌幅冷静」):取最近 5 个交易日累计净值(按日期升序,
 * T-5 在前、T-1 在末),两点跌幅 = (T-5 累计净值 - T-1 累计净值) / T-5 累计净值,
 * 正数表示下跌幅度。数据不足 5 个交易日返 {@link java.util.Optional#empty()} 降级
 * (CONTEXT.md「冷静数据不足降级」),不阻断加仓信号。
 * <p>统一用累计净值 accumulatedNav——单位净值会因分红除权让跌幅虚高。
 */
public final class WeeklyDropCalculator {

    private static final int WINDOW = 5;
    private static final MathContext MATH = MathContext.DECIMAL64;

    private WeeklyDropCalculator() {
    }

    /**
     * @param navHistory 按日期升序的累计净值序列(最旧在前、最新在末)
     * @return 单周跌幅(正数=下跌);数据不足 5 个交易日返 empty
     */
    public static java.util.Optional<BigDecimal> calculate(List<BigDecimal> navHistory) {
        if (navHistory == null || navHistory.size() < WINDOW) {
            return java.util.Optional.empty();
        }
        int last = navHistory.size() - 1;
        BigDecimal fiveDaysAgo = navHistory.get(last - (WINDOW - 1));
        BigDecimal latest = navHistory.get(last);
        BigDecimal drop = fiveDaysAgo.subtract(latest)
                .divide(fiveDaysAgo, MATH);
        return java.util.Optional.of(drop);
    }
}
