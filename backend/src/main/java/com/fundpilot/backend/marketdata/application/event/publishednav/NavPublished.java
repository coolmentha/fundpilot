package com.fundpilot.backend.marketdata.application.event.publishednav;

import java.math.BigDecimal;
import java.time.Instant;

public record NavPublished(long fundProductId, String fundCode, Instant navDate,
                           BigDecimal unitNav, BigDecimal accumulatedNav,
                           Instant firstSeenAt) {
}
