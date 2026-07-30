package com.fundpilot.backend.marketdata.infrastructure.remote.tradingcalendar;

import feign.RequestLine;

public interface SinaTradingCalendarClient {
    @RequestLine("GET /realstock/company/klc_td_sh.txt")
    String fetchTradingCalendarRaw();
}
