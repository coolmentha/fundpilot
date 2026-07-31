package com.fundpilot.backend.accounting.application.command.transactionledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TransactionLedgerCommandHandlerConversionTest {

    private static final Instant NOW = Instant.parse("2026-07-29T06:00:00Z");

    @Test
    void recordManual_转换创建不抛交易ID必须为正数并双向互指() {
        AtomicLong nextId = new AtomicLong(1);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(transactions.save(any())).thenAnswer(inv -> assignId(inv.getArgument(0), nextId));
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        when(portfolioFunds.findOwned(7L, 11L)).thenReturn(Optional.of(traded(11L)));
        when(portfolioFunds.findOwned(7L, 12L)).thenReturn(Optional.of(traded(12L)));

        TransactionLedgerCommandHandler handler = handler(transactions, portfolioFunds);

        assertThatCode(() -> handler.recordManual(7L, 11L, TransactionLedgerCommandHandler.Source.TRANSFER_OUT,
                null, new BigDecimal("100"), NOW, 12L)).doesNotThrowAnyException();

        ArgumentCaptor<LedgerTransaction> saved = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(transactions, atLeast(3)).save(saved.capture());
        LedgerTransaction outLeg = saved.getAllValues().stream()
                .filter(t -> t.source() == TransactionSource.TRANSFER_OUT).findFirst().orElseThrow();
        LedgerTransaction inLeg = saved.getAllValues().stream()
                .filter(t -> t.source() == TransactionSource.TRANSFER_IN).findFirst().orElseThrow();
        assertThat(outLeg.relatedTransactionId()).isEqualTo(inLeg.id());
        assertThat(inLeg.relatedTransactionId()).isEqualTo(outLeg.id());
        assertThat(inLeg.amount()).isNull();
        assertThat(inLeg.shares()).isNull();
    }

    @Test
    void revisePending_修改转出腿不因转入腿无金额而失败并同步交易日() {
        LedgerTransaction outLeg = LedgerTransaction.rehydrate(1L, 11L, 7L, TransactionSource.TRANSFER_OUT,
                TransactionStatus.PENDING, null, new BigDecimal("100"), null, null, null, NOW, null, null,
                NOW, 2L, null, null, null, null);
        LedgerTransaction inLeg = LedgerTransaction.rehydrate(2L, 12L, 7L, TransactionSource.TRANSFER_IN,
                TransactionStatus.PENDING, null, null, null, null, null, NOW, null, null, NOW,
                1L, null, null, null, null);
        TransactionRepository transactions = mock(TransactionRepository.class);
        when(transactions.findByIdForUpdate(1L)).thenReturn(Optional.of(outLeg));
        when(transactions.findById(2L)).thenReturn(Optional.of(inLeg));
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        TransactionLedgerCommandHandler handler = handler(transactions, mock(TradedPortfolioFundGateway.class));

        assertThatCode(() -> handler.revisePending(7L, 1L, null, new BigDecimal("80"),
                Instant.parse("2026-07-28T00:00:00Z"))).doesNotThrowAnyException();

        assertThat(inLeg.tradeDate()).isEqualTo(Instant.parse("2026-07-28T00:00:00Z"));
        assertThat(inLeg.amount()).isNull();
    }

    private static LedgerTransaction assignId(LedgerTransaction transaction, AtomicLong nextId) {
        if (transaction.id() != null) {
            return transaction;
        }
        return LedgerTransaction.rehydrate(nextId.getAndIncrement(), transaction.portfolioFundId(),
                transaction.ownerId(), transaction.source(), transaction.status(), transaction.amount(),
                transaction.shares(), transaction.nav(), transaction.fee(), transaction.feeRate(),
                transaction.tradeDate(), transaction.confirmTime(), transaction.cancelTime(),
                transaction.createdDate(), transaction.relatedTransactionId(), transaction.signalLogId(),
                transaction.dcaPlanId(), transaction.disciplineAdviceId(), transaction.investmentPlanId());
    }

    private static TransactionLedgerCommandHandler handler(TransactionRepository transactions,
                                                           TradedPortfolioFundGateway portfolioFunds) {
        return new TransactionLedgerCommandHandler(transactions, mock(LotRepository.class), portfolioFunds,
                mock(PositionCommandHandler.class), mock(LedgerEventGateway.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TradedPortfolioFundGateway.TradedPortfolioFund traded(long portfolioFundId) {
        return new TradedPortfolioFundGateway.TradedPortfolioFund(portfolioFundId, 7L, 9L, 41L, true);
    }
}
