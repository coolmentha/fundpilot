package com.fundpilot.backend.marketdata.domain.tradingcalendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradingCalendarRepository {
    boolean isTradingDay(Instant date);
    Optional<Instant> latestOnOrBefore(Instant date);
    Optional<Instant> latestBefore(Instant date);
    Optional<Instant> maxDate();
    long countBetween(Instant fromExclusive, Instant toInclusive);
    int addIfAbsent(List<TradingDay> tradingDays);
}
