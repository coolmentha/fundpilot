package com.fundpilot.backend.market.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Optional;

/**
 * 年线计算器(CONTEXT.md「调节系数表」年线维度):
 * 用累计净值序列算 250 日均线。需 251 个点——
 * 今日 250 日均线 = 末尾 250 个点均值;昨日 250 日均线 = 倒数 2..251 个点均值;
 * 通过两日均线对比判断年线是否向上(yearLineRising)。
 * 最新价 = 序列末点,priceAboveYearLine = 末点 &gt; 今日均线。
 * <p>不足 251 点返回 {@link Optional#empty()} 降级,呼叫方该基金当天不写 snapshot。
 */
public final class YearLineCalculator {

    private static final int WINDOW = 250;
    private static final int MIN_POINTS = WINDOW + 1;
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal WINDOW_DECIMAL = BigDecimal.valueOf(WINDOW);

    private YearLineCalculator() {
    }

    /**
     * @param accumulatedNav 累计净值序列,按日期升序(最旧在前、最新在末)
     * @return 250 日均线值 + 价位关系 + 均线斜率;数据不足返 empty
     */
    public static Optional<YearLineMetrics> calculate(List<BigDecimal> accumulatedNav) {
        if (accumulatedNav == null || accumulatedNav.size() < MIN_POINTS) {
            return Optional.empty();
        }
        int n = accumulatedNav.size();
        BigDecimal todaySum = BigDecimal.ZERO;
        for (int i = n - WINDOW; i < n; i++) {
            todaySum = todaySum.add(accumulatedNav.get(i));
        }
        BigDecimal yesterdaySum = BigDecimal.ZERO;
        for (int i = n - WINDOW - 1; i < n - 1; i++) {
            yesterdaySum = yesterdaySum.add(accumulatedNav.get(i));
        }
        BigDecimal todayMa = todaySum.divide(WINDOW_DECIMAL, MATH);
        BigDecimal yesterdayMa = yesterdaySum.divide(WINDOW_DECIMAL, MATH);
        BigDecimal latest = accumulatedNav.get(n - 1);

        boolean above = latest.compareTo(todayMa) > 0;
        boolean rising = todayMa.compareTo(yesterdayMa) > 0;
        return Optional.of(new YearLineMetrics(todayMa, above, rising));
    }
}
