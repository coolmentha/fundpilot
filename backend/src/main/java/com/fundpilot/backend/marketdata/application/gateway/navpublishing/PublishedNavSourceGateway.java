package com.fundpilot.backend.marketdata.application.gateway.navpublishing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface PublishedNavSourceGateway {
    List<NavSnapshot> fetchHistory(String fundCode);

    record NavSnapshot(Instant navDate, BigDecimal unitNav, BigDecimal accumulatedNav) {}
}
