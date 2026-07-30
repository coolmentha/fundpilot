package com.fundpilot.backend.marketdata.domain.indicator;

import java.time.Instant;
import java.util.Optional;

public interface MarketIndicatorRepository {
    Optional<MarketIndicator> find(long fundProductId, Instant snapshotDate);
    MarketIndicator upsert(Long legacyFundId, MarketIndicator indicator);
}
