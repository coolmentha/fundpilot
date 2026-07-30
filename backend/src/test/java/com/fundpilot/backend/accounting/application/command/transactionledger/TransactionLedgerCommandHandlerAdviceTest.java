package com.fundpilot.backend.accounting.application.command.transactionledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
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
                mock(LotRepository.class), portfolioFunds, mock(PositionCommandHandler.class),
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
                mock(LotRepository.class), portfolioFunds, mock(PositionCommandHandler.class),
                mock(LedgerEventGateway.class), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        when(portfolioFunds.findOwned(3L, 11L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(11L, 3L, 9L, 41L, true)));
        when(transactions.existsByDisciplineAdviceId(71L)).thenReturn(true);

        assertThatThrownBy(() -> handler.placePendingForAdvice(3L, 11L, TransactionLedgerCommandHandler.Source.INCREASE,
                new BigDecimal("100"), null, Instant.EPOCH, 71L))
                .isInstanceOf(TransactionLedgerFailure.class)
                .extracting(error -> ((TransactionLedgerFailure) error).code())
                .isEqualTo(TransactionLedgerFailure.Code.ADVICE_ALREADY_RESPONDED);

        verify(transactions).existsByDisciplineAdviceId(71L);
        verifyNoMoreInteractions(transactions);
    }
}
