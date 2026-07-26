package com.fundpilot.backend.marketdata.domain.indexkline;

import java.util.List;

public interface IndexKlineRepository {
    boolean exists(String indexCode);
    List<IndexBar> findAll(String indexCode);
    int upsert(List<IndexBar> bars);
}
