package com.fundpilot.backend.marketdata.domain.indexkline;

import java.util.List;
import java.time.Instant;
import java.util.Set;

public interface IndexKlineRepository {
    boolean exists(String indexCode);
    Set<String> existingCodes();
    List<IndexBar> findAll(String indexCode);
    List<IndexBar> findRefreshedBars(Instant tradeDate, Instant refreshedAfter);
    int upsert(List<IndexBar> bars);
}
