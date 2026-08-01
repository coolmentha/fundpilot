package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementFeeGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionconfirmation.SettlementNavGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.TradedPortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.lot.FeeSchedule;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import com.fundpilot.backend.platform.transaction.RequiresNewTransactionExecutor;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TransactionConfirmationCommandHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-27T08:00:00Z");

    @Test
    void confirmPendingFor_坏流水跳过_其余流水仍确认() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        SettlementFeeGateway fees = mock(SettlementFeeGateway.class);
        SettlementNavGateway navs = mock(SettlementNavGateway.class);
        PositionCommandHandler positions = mock(PositionCommandHandler.class);
        LedgerEventGateway events = mock(LedgerEventGateway.class);
        RequiresNewTransactionExecutor batchTransactions = mock(RequiresNewTransactionExecutor.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        LedgerTransaction badSell = LedgerTransaction.placePending(10L, 1L, TransactionSource.DECREASE,
                null, new BigDecimal("100"), Instant.parse("2026-07-24T00:00:00Z"), null, null);
        LedgerTransaction goodBuy = LedgerTransaction.placePending(10L, 1L, TransactionSource.INCREASE,
                new BigDecimal("100"), null, Instant.parse("2026-07-24T00:00:00Z"), null, null);

        when(transactions.findByPortfolioFundAndStatus(10L, TransactionStatus.PENDING))
                .thenReturn(List.of(badSell, goodBuy));
        when(transactions.findByPortfolioFundAndStatus(10L, TransactionStatus.CONFIRMED))
                .thenReturn(List.of());
        when(portfolioFunds.find(10L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(10L, 1L, 31L, 9L, true)));
        when(fees.feeScheduleOf(31L)).thenReturn(FeeSchedule.none());
        when(navs.unitNavOn(31L, Instant.parse("2026-07-24T00:00:00Z")))
                .thenReturn(Optional.of(new BigDecimal("2.0")));
        when(transactions.save(any(LedgerTransaction.class))).thenAnswer(invocation -> {
            LedgerTransaction tx = invocation.getArgument(0);
            return LedgerTransaction.rehydrate(500L, tx.portfolioFundId(), tx.ownerId(), tx.source(),
                    tx.status(), tx.amount(), tx.shares(), tx.nav(), tx.fee(), tx.feeRate(),
                    tx.tradeDate(), tx.confirmTime(), null, tx.createdDate(), tx.relatedTransactionId(),
                    tx.signalLogId(), tx.dcaPlanId(), tx.disciplineAdviceId(), tx.investmentPlanId());
        });

        TransactionConfirmationCommandHandler handler = new TransactionConfirmationCommandHandler(
                transactions, lots, portfolioFunds, fees, navs, positions, events, batchTransactions, clock);

        int confirmed = handler.confirmPendingFor(10L, Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(confirmed).isEqualTo(1);
        verify(transactions).save(goodBuy);
        verify(lots).save(any());
        verify(transactions, never()).save(badSell);
    }
}
