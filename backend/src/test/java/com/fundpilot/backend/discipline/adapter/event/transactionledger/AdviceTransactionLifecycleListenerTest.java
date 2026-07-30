package com.fundpilot.backend.discipline.adapter.event.transactionledger;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.fundpilot.backend.accounting.application.event.transaction.TransactionCancelled;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.discipline.application.command.advicelifecycle.AdviceLifecycleCommandHandler;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AdviceTransactionLifecycleListenerTest {
    @Test
    void delegatesOnlyAdviceLinkedTransactions() {
        var lifecycle = mock(AdviceLifecycleCommandHandler.class);
        var listener = new AdviceTransactionLifecycleListener(lifecycle);
        Instant now = Instant.parse("2026-07-29T00:00:00Z");

        listener.onConfirmed(new TransactionConfirmed(1L, 11L, 7L, "INCREASE", BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, now, now, null, null, 31L, null,
                1L, now));
        listener.onCancelled(new TransactionCancelled(2L, 11L, 7L, "INCREASE", null, null,
                32L, null, now, 1L, now));
        listener.onConfirmed(new TransactionConfirmed(3L, 11L, 7L, "MANUAL", BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, now, now, null, null, null, null,
                1L, now));

        verify(lifecycle).confirmed(31L);
        verify(lifecycle).cancelled(32L);
        verifyNoMoreInteractions(lifecycle);
    }
}
