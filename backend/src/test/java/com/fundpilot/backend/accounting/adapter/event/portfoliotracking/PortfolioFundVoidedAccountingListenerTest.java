package com.fundpilot.backend.accounting.adapter.event.portfoliotracking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PortfolioFundVoidedAccountingListenerTest {
    @Test
    void removesOnlyTheRebuildablePositionProjection() {
        PositionCommandHandler positions = mock(PositionCommandHandler.class);
        var listener = new PortfolioFundVoidedAccountingListener(positions);

        listener.onVoided(new PortfolioFundVoidedEvent(12L, 7L, 31L, 9L, "录入错误",
                Instant.parse("2026-07-29T00:00:00Z")));

        verify(positions).removeVoidedProjection(12L);
    }
}
