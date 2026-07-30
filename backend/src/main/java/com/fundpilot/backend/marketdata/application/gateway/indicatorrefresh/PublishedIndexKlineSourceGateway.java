package com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PublishedIndexKlineSourceGateway {
    IndexKline fetch(String secid, String limit);

    record IndexKline(List<Bar> bars) {}
    record Bar(Instant tradeDate, BigDecimal open, BigDecimal high, BigDecimal low,
               BigDecimal close, long volume) {}
}
