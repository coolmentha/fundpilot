package com.fundpilot.backend.marketdata.application.command.watchedindex;

import com.fundpilot.backend.marketdata.application.event.watchedindex.WatchedIndicesChanged;
import com.fundpilot.backend.marketdata.application.gateway.watchedindex.WatchedIndexEventGateway;
import com.fundpilot.backend.marketdata.domain.watchedindex.WatchedIndexList;
import com.fundpilot.backend.marketdata.domain.watchedindex.WatchedIndexRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatchedIndexCommandHandler {
    private final WatchedIndexRepository watchlists;
    private final WatchedIndexEventGateway events;
    @Transactional public Result replace(long ownerId, List<String> indexCodes) {
        WatchedIndexList saved = watchlists.replace(new WatchedIndexList(ownerId, indexCodes));
        events.publishWatchedIndicesChanged(new WatchedIndicesChanged(saved.ownerId(), saved.indexCodes()));
        return new Result(saved.ownerId(), saved.indexCodes());
    }
    public record Result(long ownerId, List<String> indexCodes) {}
}
