package com.fundpilot.backend.investmentplan.adapter.event.portfoliotracking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fundpilot.backend.investmentplan.application.command.planlifecycle.InvestmentPlanLifecycleCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvestmentPlanPortfolioFundListenerTest {
    @Test
    void delegatesPortfolioFundVoidedEvent() {
        var lifecycle = mock(InvestmentPlanLifecycleCommandHandler.class);
        var listener = new InvestmentPlanPortfolioFundListener(lifecycle);

        listener.onVoided(new PortfolioFundVoidedEvent(13L, 7L, 21L, 7L, "录入错误",
                Instant.parse("2026-07-29T00:00:00Z")));

        verify(lifecycle).portfolioFundVoided(13L);
    }
}
