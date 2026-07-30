package com.fundpilot.backend.marketdata.domain.tradingcalendar;

import java.time.Instant;
import java.util.Objects;

public record TradingDay(Instant calendarDate) {
    public TradingDay {
        Objects.requireNonNull(calendarDate, "交易日不能为空");
    }
}
