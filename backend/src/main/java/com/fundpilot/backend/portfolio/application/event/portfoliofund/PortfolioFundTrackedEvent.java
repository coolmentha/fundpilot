package com.fundpilot.backend.portfolio.application.event.portfoliofund;

import java.time.Instant;

public record PortfolioFundTrackedEvent(long portfolioFundId, long ownerId, long fundProductId,
                                        Instant occurredAt) {
}
