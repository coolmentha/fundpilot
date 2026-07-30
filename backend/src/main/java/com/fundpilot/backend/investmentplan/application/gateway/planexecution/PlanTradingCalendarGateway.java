package com.fundpilot.backend.investmentplan.application.gateway.planexecution;

import java.time.Instant;
import java.util.Optional;

/** 定投执行使用 MarketData 维护的交易日事实。 */
public interface PlanTradingCalendarGateway {
    boolean isTradingDay(Instant businessDate);
    Optional<Instant> latestBefore(Instant businessDate);
    java.util.List<Instant> tradingDaysBetween(Instant startInclusive, Instant endExclusive);
}
