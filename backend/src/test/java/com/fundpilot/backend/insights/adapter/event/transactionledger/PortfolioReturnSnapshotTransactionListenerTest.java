package com.fundpilot.backend.insights.adapter.event.transactionledger;

import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.insights.application.command.portfolioreturn.PortfolioReturnSnapshotCommandHandler;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PortfolioReturnSnapshotTransactionListenerTest {

    @Test
    void confirmedHistoricalTransactionRecapturesExistingTrendFromItsBusinessDay() {
        PortfolioReturnSnapshotCommandHandler snapshots = mock(PortfolioReturnSnapshotCommandHandler.class);
        var listener = new PortfolioReturnSnapshotTransactionListener(snapshots);
        listener.onConfirmed(new TransactionConfirmed(1L, 12L, 7L, "INCREASE",
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO,
                Instant.parse("2026-07-28T20:00:00Z"), Instant.parse("2026-07-30T00:00:00Z"),
                null, null, null, null, 1L, Instant.parse("2026-07-30T00:00:00Z")));

        verify(snapshots).recaptureExistingFrom(7L, Instant.parse("2026-07-29T00:00:00Z"));
    }
}
