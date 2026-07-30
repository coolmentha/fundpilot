package com.fundpilot.backend.marketdata.infrastructure.remote.tradingcalendar;

import com.fundpilot.backend.marketdata.application.gateway.tradingcalendar.TradingCalendarSourceGateway;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class TradingCalendarSourceGatewayImpl implements TradingCalendarSourceGateway {
    private final SinaTradingCalendarClient client;

    @Override
    public List<Instant> fetchTradingDays() {
        return SinaTradingCalendarParser.parse(client.fetchTradingCalendarRaw());
    }
}
