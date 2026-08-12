package com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PublishedIndexValuationSourceGateway {
    List<Valuation> fetch(String indexCode, String startDate, String endDate);
    record Valuation(Instant tradeDate, BigDecimal peRatio) {}
}
