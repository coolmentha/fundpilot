package com.fundpilot.backend.discipline.adapter.event.strategylifecycle;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fundpilot.backend.accounting.application.event.position.PositionCleared;
import com.fundpilot.backend.accounting.application.event.position.PositionOpened;
import com.fundpilot.backend.discipline.application.command.strategylifecycle.DisciplineStrategyLifecycleCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DisciplineStrategyLifecycleListenerTest {
    @Test
    void delegatesPositionAndPortfolioLifecycleEvents() {
        var lifecycle = mock(DisciplineStrategyLifecycleCommandHandler.class);
        var listener = new DisciplineStrategyLifecycleListener(lifecycle);
        Instant now = Instant.parse("2026-07-29T00:00:00Z");

        listener.onPositionOpened(new PositionOpened(11L, 7L, now, 2L, now));
        listener.onPositionCleared(new PositionCleared(12L, 7L, 3L, now));
        listener.onPortfolioFundVoided(new PortfolioFundVoidedEvent(13L, 7L, 21L, 7L, "录入错误", now));

        verify(lifecycle).positionOpened(11L);
        verify(lifecycle).positionCleared(12L);
        verify(lifecycle).portfolioFundVoided(13L);
    }
}
