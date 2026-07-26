package com.fundpilot.backend.marketdata.infrastructure.messaging.navpublishing;

import com.fundpilot.backend.marketdata.application.event.publishednav.NavPublished;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavEventGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublishedNavEventGatewayImpl implements PublishedNavEventGateway {
    private final ApplicationEventPublisher events;

    @Override
    public void publishNavPublished(NavPublished event) {
        events.publishEvent(event);
    }
}
