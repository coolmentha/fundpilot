package com.fundpilot.backend.marketdata.adapter.api.watchedindex;

import com.fundpilot.backend.marketdata.application.command.watchedindex.WatchedIndexCommandHandler;
import com.fundpilot.backend.marketdata.application.query.watchedindex.WatchedIndexQueryHandler;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WatchedIndicesApi {
    public static final List<String> DEFAULT_INDICES = WatchedIndexQueryHandler.DEFAULT_INDICES;
    private final WatchedIndexCommandHandler commands;
    private final WatchedIndexQueryHandler queries;
    public List<String> findByOwner(long ownerId) { return queries.findByOwner(ownerId); }
    public List<String> findAllForRefresh() { return queries.findAllDistinctCodes(); }
    public List<String> replace(long ownerId, List<String> codes) { return commands.replace(ownerId, codes).indexCodes(); }
}
