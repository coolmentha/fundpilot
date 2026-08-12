package com.fundpilot.backend.marketdata.domain.indexvaluation;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IndexValuationRepository {
    List<IndexValuation> findHistory(String indexCode, String source, Instant endExclusive);
    Optional<IndexValuation> findLatest(String indexCode, String source);
    int upsert(List<IndexValuation> valuations);
}
