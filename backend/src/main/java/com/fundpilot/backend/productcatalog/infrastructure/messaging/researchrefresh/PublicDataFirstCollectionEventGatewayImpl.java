package com.fundpilot.backend.productcatalog.infrastructure.messaging.researchrefresh;

import com.fundpilot.backend.productcatalog.application.event.researchrefresh.FundPublicDataRefreshRequestedEvent;
import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.PublicDataFirstCollectionEventGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublicDataFirstCollectionEventGatewayImpl implements PublicDataFirstCollectionEventGateway {
    private final ApplicationEventPublisher events;

    @Override
    public void publishRefreshRequested(String fundCode) {
        events.publishEvent(new FundPublicDataRefreshRequestedEvent(fundCode));
    }
}
