package com.fundpilot.backend.strategy.service;

import java.time.Instant;

/**
 * 回测窗口(issue #11):起止日期区间。
 * <p>调用方传「过去一年」;基金成立不满一年时 {@code startDate} 由 #11 实现自动降级为最早可用日期。
 *
 * @param startDate 回测起始日期(含)
 * @param endDate   回测结束日期(含)
 */
public record BacktestWindow(Instant startDate, Instant endDate) {

    /** 回测窗口天数(issue #11:过去一年)。 */
    public static final int BACKTEST_WINDOW_DAYS = 365;
}
