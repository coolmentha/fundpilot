package com.fundpilot.backend.insights.application.gateway.portfolioreturn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReturnSnapshotSchedulingGateway {
    Optional<Instant> latestTradingDayBefore(Instant date);
    List<Long> activeOwnerIds();
    void runAsSystem(long ownerId, Runnable action);
}
