package com.fundpilot.backend.market.service.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 60 日新高判定(CONTEXT.md「调节系数表」与信号引擎):取最近 60 个交易日的累计净值,
 * 最新累计净值等于窗口内最大值即视为 60 日新高。窗口不足 60 天返回 {@link Optional#empty()} 降级。
 */
public final class SixtyDayHighCalculator {

    private static final int WINDOW = 60;

    private SixtyDayHighCalculator() {
    }

    /**
     * @param accumulatedNav 累计净值序列,按日期升序(最旧在前、最新在末)
     * @return 最新累计净值是否等于最近 60 个交易日内的最大值;数据不足返 empty
     */
    public static Optional<Boolean> calculate(List<BigDecimal> accumulatedNav) {
        if (accumulatedNav == null || accumulatedNav.size() < WINDOW) {
            return Optional.empty();
        }
        int n = accumulatedNav.size();
        BigDecimal latest = accumulatedNav.get(n - 1);
        BigDecimal max = accumulatedNav.get(n - WINDOW);
        for (int i = n - WINDOW + 1; i < n; i++) {
            BigDecimal v = accumulatedNav.get(i);
            if (v.compareTo(max) > 0) {
                max = v;
            }
        }
        return Optional.of(latest.compareTo(max) >= 0);
    }
}
