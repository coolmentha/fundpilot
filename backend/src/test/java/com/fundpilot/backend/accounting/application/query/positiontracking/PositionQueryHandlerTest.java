package com.fundpilot.backend.accounting.application.query.positiontracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.position.PositionStatus;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PositionQueryHandlerTest {
    @Test
    void findOpenLots_excludesAnotherOwnersPosition() {
        PositionRepository positions = mock(PositionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        when(positions.findByPortfolioFund(12L)).thenReturn(Optional.of(
                Position.rehydrate(8L, 1L, 12L, 2L, PositionStatus.OPEN, null, BigDecimal.ONE)));

        assertThat(new PositionQueryHandler(positions, mock(TransactionRepository.class), lots)
                .findOpenLots(3L, 12L)).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(lots);
    }

    @Test
    void findOwned_excludesAnotherOwnersPosition() {
        PositionRepository positions = mock(PositionRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        Position position = Position.rehydrate(8L, 1L, 12L, 2L, PositionStatus.OPEN, null,
                new BigDecimal("1.20"));
        when(positions.findByPortfolioFund(12L)).thenReturn(Optional.of(position));

        assertThat(new PositionQueryHandler(positions, transactions, mock(LotRepository.class))
                .findOwned(3L, 12L)).isEmpty();
    }

    @Test
    void findByOwner_includesConfirmedSharesAndDefaultsToZero() {
        PositionRepository positions = mock(PositionRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        Position open = Position.rehydrate(8L, 1L, 12L, 2L, PositionStatus.OPEN, null,
                new BigDecimal("1.20"));
        Position empty = Position.rehydrate(9L, 1L, 13L, 2L, PositionStatus.EMPTY, null, null);
        when(positions.findByOwner(2L)).thenReturn(List.of(open, empty));
        when(transactions.aggregateConfirmedShares(List.of(12L, 13L))).thenReturn(List.of(
                new TransactionRepository.HoldingShares(12L, new BigDecimal("23.45"))));

        assertThat(new PositionQueryHandler(positions, transactions, mock(LotRepository.class)).findByOwner(2L))
                .extracting(PositionQueryHandler.PositionResult::portfolioFundId,
                        PositionQueryHandler.PositionResult::confirmedShares)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(12L, new BigDecimal("23.45")),
                        org.assertj.core.groups.Tuple.tuple(13L, BigDecimal.ZERO));
    }

    @Test
    void findByOwnerAtReplaysOnlyTransactionsBeforeObservationEnd() {
        PositionRepository positions = mock(PositionRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        Position position = Position.rehydrate(8L, 1L, 12L, 2L, PositionStatus.OPEN, null,
                new BigDecimal("99"));
        var first = transaction(1L, "100", "10", "2026-01-01T00:00:00Z");
        var future = transaction(2L, "240", "20", "2026-01-01T20:00:00Z");
        when(positions.findByOwner(2L)).thenReturn(List.of(position));
        when(transactions.findByPortfolioFundIdsAndStatus(List.of(12L), TransactionStatus.CONFIRMED))
                .thenReturn(List.of(first, future));

        var result = new PositionQueryHandler(positions, transactions, mock(LotRepository.class))
                .findByOwnerAt(2L, Instant.parse("2026-01-02T00:00:00Z")).getFirst();

        assertThat(result.confirmedShares()).isEqualByComparingTo("10");
        assertThat(result.costPerShare()).isEqualByComparingTo("10");
        assertThat(result.status()).isEqualTo("OPEN");
    }

    private static LedgerTransaction transaction(long id, String amount, String shares, String tradeDate) {
        Instant occurredAt = Instant.parse(tradeDate);
        return LedgerTransaction.rehydrate(id, 12L, 2L, TransactionSource.INCREASE,
                TransactionStatus.CONFIRMED, new BigDecimal(amount), new BigDecimal(shares), BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO, occurredAt, occurredAt, null, occurredAt,
                null, null, null, null, null);
    }
}
