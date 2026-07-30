package com.fundpilot.backend.marketdata.infrastructure.messaging.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.event.indicatorrefresh.MarketIndicatorsRefreshed;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.MarketIndicatorRefreshEventGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MarketIndicatorRefreshEventGatewayImpl implements MarketIndicatorRefreshEventGateway {
    private final ApplicationEventPublisher events;

    @Override
    public void publishMarketIndicatorsRefreshed(MarketIndicatorsRefreshed event) {
        events.publishEvent(event);
    }
}
