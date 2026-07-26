package com.fundpilot.backend.marketdata.application.event.watchedindex;

import java.util.List;

public record WatchedIndicesChanged(long ownerId, List<String> indexCodes) {
    public WatchedIndicesChanged { indexCodes = List.copyOf(indexCodes); }
}
