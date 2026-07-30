package com.fundpilot.backend.insights.domain.portfolioreturn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PortfolioReturnSnapshotRepository {
    Optional<PortfolioReturnSnapshot> find(long ownerId, Instant businessDate);
    Optional<PortfolioReturnSnapshot> latestBefore(long ownerId, Instant businessDate);
    List<PortfolioReturnSnapshot> between(long ownerId, Instant from, Instant to);
    PortfolioReturnSnapshot save(PortfolioReturnSnapshot snapshot);
}
