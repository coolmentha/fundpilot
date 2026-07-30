package com.fundpilot.backend.portfolio.domain.portfoliofund;

import java.time.Instant;

public record PortfolioFundVoided(
        long portfolioFundId,
        long ownerId,
        long fundProductId,
        long voidedBy,
        String reason,
        Instant occurredAt
) {
}
