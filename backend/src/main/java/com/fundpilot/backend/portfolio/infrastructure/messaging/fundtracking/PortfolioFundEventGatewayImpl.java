package com.fundpilot.backend.portfolio.infrastructure.messaging.fundtracking;

import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundTrackedEvent;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import com.fundpilot.backend.portfolio.application.gateway.fundtracking.PortfolioFundEventGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PortfolioFundEventGatewayImpl implements PortfolioFundEventGateway {
    private final ApplicationEventPublisher events;

    @Override
    public void publishTracked(PortfolioFundTrackedEvent event) {
        events.publishEvent(event);
    }

    @Override
    public void publishVoided(PortfolioFundVoidedEvent event) {
        events.publishEvent(event);
    }
}
