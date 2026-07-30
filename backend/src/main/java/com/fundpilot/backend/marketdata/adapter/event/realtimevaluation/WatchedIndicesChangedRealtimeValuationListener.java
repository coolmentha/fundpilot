package com.fundpilot.backend.marketdata.adapter.event.realtimevaluation;

import com.fundpilot.backend.marketdata.application.command.realtimevaluation.RealtimeValuationRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.event.watchedindex.WatchedIndicesChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WatchedIndicesChangedRealtimeValuationListener {
    private final RealtimeValuationRefreshCommandHandler commands;

    @ApplicationModuleListener
    public void onWatchedIndicesChanged(WatchedIndicesChanged event) {
        commands.refreshIndices();
    }
}
