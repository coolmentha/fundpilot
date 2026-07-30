package com.fundpilot.backend.marketdata.application.gateway.tradingcalendar;

import java.time.Instant;
import java.util.List;

public interface TradingCalendarSourceGateway {
    List<Instant> fetchTradingDays();
}
