package com.fundpilot.backend.portfolio.application.event.portfoliofund;

import java.time.Instant;

public record PortfolioFundVoidedEvent(long portfolioFundId, long ownerId, long fundProductId,
                                       long voidedBy, String reason, Instant occurredAt) {
}
