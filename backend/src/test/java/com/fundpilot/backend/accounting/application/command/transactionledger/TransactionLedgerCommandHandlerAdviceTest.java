package com.fundpilot.backend.accounting.application.command.transactionledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradingDayGateway;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionLedgerCommandHandlerAdviceTest {

    @Test
    void legacy基金交易拒绝访问其他用户的组合基金() {
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        TransactionLedgerCommandHandler handler = new TransactionLedgerCommandHandler(mock(TransactionRepository.class),
                mock(LotRepository.class), portfolioFunds, mock(TradingDayGateway.class),
                mock(PositionCommandHandler.class),
                mock(LedgerEventGateway.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        when(portfolioFunds.findByLegacyFundId(41L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(11L, 8L, 9L, 41L, true)));

        assertThatThrownBy(() -> handler.recordManualForLegacyFund(7L, 41L,
                TransactionLedgerCommandHandler.Source.INCREASE, BigDecimal.ONE, null, Instant.EPOCH, null))
                .isInstanceOf(TransactionLedgerFailure.class)
                .extracting(error -> ((TransactionLedgerFailure) error).code())
                .isEqualTo(TransactionLedgerFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    @Test
    void placePendingForAdvice_重复建议在写账目前拒绝() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        TransactionLedgerCommandHandler handler = new TransactionLedgerCommandHandler(transactions,
                mock(LotRepository.class), portfolioFunds, mock(TradingDayGateway.class),
                mock(PositionCommandHandler.class),
                mock(LedgerEventGateway.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        when(portfolioFunds.findOwned(3L, 11L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(11L, 3L, 9L, 41L, true)));
        when(transactions.existsByDisciplineAdviceIdAndStatusNot(71L, TransactionStatus.CANCELLED)).thenReturn(true);

        assertThatThrownBy(() -> handler.placePendingForAdvice(3L, 11L, TransactionLedgerCommandHandler.Source.INCREASE,
                new BigDecimal("100"), null, Instant.EPOCH, 71L, "LOGIC_BROKEN"))
                .isInstanceOf(TransactionLedgerFailure.class)
                .extracting(error -> ((TransactionLedgerFailure) error).code())
                .isEqualTo(TransactionLedgerFailure.Code.ADVICE_ALREADY_RESPONDED);

        verify(transactions).existsByDisciplineAdviceIdAndStatusNot(71L, TransactionStatus.CANCELLED);
        verifyNoMoreInteractions(transactions);
    }

    @Test
    void placePendingForAdvice_撤单后重新接受不再视为已回应() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        TransactionLedgerCommandHandler handler = new TransactionLedgerCommandHandler(transactions,
                mock(LotRepository.class), portfolioFunds, mock(TradingDayGateway.class),
                mock(PositionCommandHandler.class),
                mock(LedgerEventGateway.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        when(portfolioFunds.findOwned(3L, 11L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(11L, 3L, 9L, 41L, true)));
        when(transactions.existsByDisciplineAdviceIdAndStatusNot(71L, TransactionStatus.CANCELLED))
                .thenReturn(false);
        java.util.concurrent.atomic.AtomicLong nextId = new java.util.concurrent.atomic.AtomicLong(1);
        when(transactions.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> {
            var t = (com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction) inv.getArgument(0);
            return com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction.rehydrate(
                    nextId.getAndIncrement(), t.portfolioFundId(), t.ownerId(), t.source(), t.status(),
                    t.amount(), t.shares(), t.nav(), t.fee(), t.feeRate(), t.tradeDate(), t.confirmTime(),
                    t.cancelTime(), t.createdDate(), t.relatedTransactionId(), t.signalLogId(), t.dcaPlanId(),
                    t.disciplineAdviceId(), t.investmentPlanId());
        });

        var result = handler.placePendingForAdvice(3L, 11L,
                TransactionLedgerCommandHandler.Source.INCREASE,
                new BigDecimal("100"), null, Instant.EPOCH, 71L, "LOGIC_BROKEN");

        org.assertj.core.api.Assertions.assertThat(result).isNotNull();
        verify(transactions).existsByDisciplineAdviceIdAndStatusNot(71L, TransactionStatus.CANCELLED);
        verify(transactions).save(org.mockito.ArgumentMatchers.any());
    }
}
