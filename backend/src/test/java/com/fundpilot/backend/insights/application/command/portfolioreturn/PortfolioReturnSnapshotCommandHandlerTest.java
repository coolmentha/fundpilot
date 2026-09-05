package com.fundpilot.backend.insights.application.command.portfolioreturn;

import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnSnapshotSchedulingGateway;
import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import org.mockito.ArgumentCaptor;

class PortfolioReturnSnapshotCommandHandlerTest {

    @Test
    void captureUsesFactsAtSnapshotBusinessDate() {
        PortfolioReturnSnapshotRepository snapshots = mock(PortfolioReturnSnapshotRepository.class);
        PortfolioReturnQueryHandler returns = mock(PortfolioReturnQueryHandler.class);
        Instant businessDate = Instant.parse("2026-07-29T00:00:00Z");
        var result = new PortfolioReturnQueryHandler.PortfolioReturnResult(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, true, List.of());
        when(returns.findByOwnerAt(7L, businessDate)).thenReturn(result);
        when(snapshots.find(7L, businessDate)).thenReturn(Optional.empty());

        new PortfolioReturnSnapshotCommandHandler(snapshots, returns,
                Clock.fixed(Instant.parse("2026-07-30T01:00:00Z"), ZoneOffset.UTC),
                mock(ReturnSnapshotSchedulingGateway.class)).capture(7L, businessDate);

        verify(returns).findByOwnerAt(7L, businessDate);
        verify(returns, never()).findByOwner(anyLong());
        verify(snapshots).save(any());
    }

    @Test
    void recaptureExistingFromRewritesAffectedPersistedTrendPoints() {
        PortfolioReturnSnapshotRepository snapshots = mock(PortfolioReturnSnapshotRepository.class);
        PortfolioReturnQueryHandler returns = mock(PortfolioReturnQueryHandler.class);
        Instant businessDate = Instant.parse("2026-07-28T00:00:00Z");
        Instant today = Instant.parse("2026-07-30T00:00:00Z");
        var existing = new PortfolioReturnSnapshot(1L, 7L, businessDate, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, true, "", businessDate);
        var result = new PortfolioReturnQueryHandler.PortfolioReturnResult(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, true, List.of());
        when(snapshots.between(7L, businessDate, today)).thenReturn(List.of(existing));
        when(snapshots.find(7L, businessDate)).thenReturn(Optional.of(existing));
        when(returns.findByOwnerAt(7L, businessDate)).thenReturn(result);

        new PortfolioReturnSnapshotCommandHandler(snapshots, returns,
                Clock.fixed(Instant.parse("2026-07-30T08:00:00Z"), ZoneOffset.UTC),
                mock(ReturnSnapshotSchedulingGateway.class)).recaptureExistingFrom(7L, businessDate);

        verify(returns).findByOwnerAt(7L, businessDate);
        verify(snapshots).save(any());
    }

    @Test
    void capturePersistsPartialSnapshotWithMissingFundCoverage() {
        PortfolioReturnSnapshotRepository snapshots = mock(PortfolioReturnSnapshotRepository.class);
        PortfolioReturnQueryHandler returns = mock(PortfolioReturnQueryHandler.class);
        Instant businessDate = Instant.parse("2026-07-29T00:00:00Z");
        var missingFund = mock(PortfolioReturnQueryHandler.FundReturnResult.class);
        when(missingFund.open()).thenReturn(true);
        when(missingFund.fundCode()).thenReturn("000003");
        when(missingFund.unrealizedPnl()).thenReturn(null);
        when(missingFund.holdingAmount()).thenReturn(null);
        when(missingFund.totalReturn()).thenReturn(null);
        var result = new PortfolioReturnQueryHandler.PortfolioReturnResult(BigDecimal.TEN, BigDecimal.ZERO,
                BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, null, true, List.of(missingFund));
        when(returns.findByOwnerAt(7L, businessDate)).thenReturn(result);
        when(snapshots.find(7L, businessDate)).thenReturn(Optional.empty());

        new PortfolioReturnSnapshotCommandHandler(snapshots, returns,
                Clock.fixed(Instant.parse("2026-07-30T01:00:00Z"), ZoneOffset.UTC),
                mock(ReturnSnapshotSchedulingGateway.class)).capture(7L, businessDate);

        var captor = ArgumentCaptor.forClass(PortfolioReturnSnapshot.class);
        verify(snapshots).save(captor.capture());
        assertThat(captor.getValue().valuationComplete()).isFalse();
        assertThat(captor.getValue().missingFundCodes()).isEqualTo("000003");
        assertThat(captor.getValue().holdingAmount()).isZero();
        assertThat(captor.getValue().totalReturn()).isZero();
    }
}
