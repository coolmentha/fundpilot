package com.fundpilot.backend.marketdata.adapter.api.tradingcalendar;

import com.fundpilot.backend.marketdata.application.command.tradingcalendar.TradingCalendarCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TradingCalendarApi {
    private final TradingCalendarCommandHandler commands;
    private final TradingCalendarQueryHandler queries;

    public int addTradingDays(List<Instant> dates) { return commands.addTradingDays(dates); }
    public boolean isTradingDay(Instant date) { return queries.isTradingDay(date); }
    public Optional<Instant> latestOnOrBefore(Instant date) { return queries.latestOnOrBefore(date); }
    public Optional<Instant> latestBefore(Instant date) { return queries.latestBefore(date); }
    public Optional<Instant> maxDate() { return queries.maxDate(); }
    public long countBetween(Instant fromExclusive, Instant toInclusive) {
        return queries.countBetween(fromExclusive, toInclusive);
    }
}
