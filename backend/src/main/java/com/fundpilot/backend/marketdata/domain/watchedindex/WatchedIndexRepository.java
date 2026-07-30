package com.fundpilot.backend.marketdata.domain.watchedindex;

import java.util.List;

public interface WatchedIndexRepository {
    WatchedIndexList findByOwnerId(long ownerId);
    List<String> findAllDistinctCodes();
    WatchedIndexList replace(WatchedIndexList watchlist);
}
