package com.fundpilot.backend.investmentplan.infrastructure.gateway.planexecution;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTradingCalendarGateway;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlanTradingCalendarGatewayImpl implements PlanTradingCalendarGateway {
    private final TradingCalendarApi calendar;
    @Override public boolean isTradingDay(Instant businessDate) { return calendar.isTradingDay(businessDate); }
    @Override public java.util.Optional<Instant> latestBefore(Instant businessDate) {
        return calendar.latestBefore(businessDate);
    }
    @Override public java.util.List<Instant> tradingDaysBetween(Instant startInclusive, Instant endExclusive) {
        return calendar.tradingDaysBetween(startInclusive, endExclusive);
    }
}
