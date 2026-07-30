package com.fundpilot.backend.marketdata.infrastructure.messaging.watchedindex;

import com.fundpilot.backend.marketdata.application.event.watchedindex.WatchedIndicesChanged;
import com.fundpilot.backend.marketdata.application.gateway.watchedindex.WatchedIndexEventGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WatchedIndexEventGatewayImpl implements WatchedIndexEventGateway {
    private final ApplicationEventPublisher events;

    @Override
    public void publishWatchedIndicesChanged(WatchedIndicesChanged event) {
        events.publishEvent(event);
    }
}
