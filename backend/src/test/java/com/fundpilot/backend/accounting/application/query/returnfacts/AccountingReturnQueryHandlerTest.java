package com.fundpilot.backend.accounting.application.query.returnfacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRedemption;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.position.PositionStatus;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccountingReturnQueryHandlerTest {
    @Test
    void findByOwner_calculatesCashFlowsFeesAndRealizedPnlFromLots() {
        PositionRepository positions = mock(PositionRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        Position position = Position.rehydrate(1L, 0L, 12L, 7L, PositionStatus.OPEN, null, null);
        LedgerTransaction buy = transaction(101L, 12L, TransactionSource.INCREASE, "100", "10", "1");
        LedgerTransaction sell = transaction(102L, 12L, TransactionSource.DECREASE, "80", "5", "2");
        Lot lot = Lot.rehydrate(201L, 12L, 101L, Instant.parse("2026-01-01T00:00:00Z"),
                new BigDecimal("10"), new BigDecimal("5"), new BigDecimal("10"));
        when(positions.findByOwner(7L)).thenReturn(List.of(position));
        when(transactions.findByPortfolioFundIdsAndStatus(List.of(12L), TransactionStatus.CONFIRMED))
                .thenReturn(List.of(buy, sell));
        when(lots.findByPortfolioFundIds(List.of(12L))).thenReturn(List.of(lot));
        when(lots.findRedemptionsBySellTransactionIds(List.of(102L))).thenReturn(List.of(
                new LotRedemption(301L, 201L, 102L, new BigDecimal("5"), 5, BigDecimal.ZERO)));

        var fact = new AccountingReturnQueryHandler(positions, transactions, lots).findByOwner(7L).getFirst();

        assertThat(fact.investedAmount()).isEqualByComparingTo("100");
        assertThat(fact.redeemedAmount()).isEqualByComparingTo("80");
        assertThat(fact.externalInvestedAmount()).isEqualByComparingTo("100");
        assertThat(fact.externalRedeemedAmount()).isEqualByComparingTo("80");
        assertThat(fact.feeAmount()).isEqualByComparingTo("3");
        assertThat(fact.realizedPnl()).isEqualByComparingTo("30");
        assertThat(fact.realizedComplete()).isTrue();
    }

    private static LedgerTransaction transaction(long id, long portfolioFundId, TransactionSource source,
                                                 String amount, String shares, String fee) {
        return LedgerTransaction.rehydrate(id, portfolioFundId, 7L, source, TransactionStatus.CONFIRMED,
                new BigDecimal(amount), new BigDecimal(shares), null, new BigDecimal(fee), null,
                Instant.parse("2026-01-02T00:00:00Z"), Instant.parse("2026-01-03T00:00:00Z"), null,
                Instant.parse("2026-01-02T00:00:00Z"), null, null, null, null, null);
    }
}
