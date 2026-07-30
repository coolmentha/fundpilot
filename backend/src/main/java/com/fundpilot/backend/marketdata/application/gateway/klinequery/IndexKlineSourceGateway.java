package com.fundpilot.backend.marketdata.application.gateway.klinequery;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface IndexKlineSourceGateway {
    List<Bar> fetch(String secid, String period, String limit);

    record Bar(Instant tradeDate, BigDecimal open, BigDecimal high, BigDecimal low,
               BigDecimal close, long volume) {}
}
