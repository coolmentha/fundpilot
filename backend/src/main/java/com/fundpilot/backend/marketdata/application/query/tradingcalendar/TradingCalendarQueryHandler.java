package com.fundpilot.backend.marketdata.application.query.tradingcalendar;

import com.fundpilot.backend.marketdata.domain.tradingcalendar.TradingCalendarRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TradingCalendarQueryHandler {
    private final TradingCalendarRepository calendar;

    @Transactional(readOnly = true) public boolean isTradingDay(Instant date) {
        return calendar.isTradingDay(date);
    }
    @Transactional(readOnly = true) public Optional<Instant> latestOnOrBefore(Instant date) {
        return calendar.latestOnOrBefore(date);
    }
    @Transactional(readOnly = true) public Optional<Instant> latestBefore(Instant date) {
        return calendar.latestBefore(date);
    }
    @Transactional(readOnly = true) public Optional<Instant> maxDate() {
        return calendar.maxDate();
    }
    @Transactional(readOnly = true) public long countBetween(Instant fromExclusive, Instant toInclusive) {
        return calendar.countBetween(fromExclusive, toInclusive);
    }
}
