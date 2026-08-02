package com.fundpilot.backend.accounting.application.command.transactionconfirmation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
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
import org.mockito.InOrder;
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

        LedgerTransaction badSell = LedgerTransaction.rehydrate(501L, 10L, 1L, TransactionSource.DECREASE,
                TransactionStatus.PENDING, null, new BigDecimal("100"), null, null, null,
                Instant.parse("2026-07-24T00:00:00Z"), null, null, null, null, null, null, null, null);
        LedgerTransaction goodBuy = LedgerTransaction.rehydrate(502L, 10L, 1L, TransactionSource.INCREASE,
                TransactionStatus.PENDING, new BigDecimal("100"), null, null, null, null,
                Instant.parse("2026-07-24T00:00:00Z"), null, null, null, null, null, null, null, null);

        when(transactions.findByPortfolioFundAndStatus(10L, TransactionStatus.PENDING))
                .thenReturn(List.of(badSell, goodBuy));
        when(transactions.findByPortfolioFundAndStatus(10L, TransactionStatus.CONFIRMED))
                .thenReturn(List.of());
        when(portfolioFunds.findForUpdate(10L)).thenReturn(Optional.of(
                new TradedPortfolioFundGateway.TradedPortfolioFund(10L, 1L, 31L, 9L, true)));
        when(transactions.findByIdForUpdate(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            if (id == 501L) return Optional.of(badSell);
            if (id == 502L) return Optional.of(goodBuy);
            return Optional.empty();
        });
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
        InOrder order = inOrder(portfolioFunds, transactions);
        order.verify(portfolioFunds).findForUpdate(10L);
        order.verify(transactions).findByPortfolioFundAndStatus(10L, TransactionStatus.PENDING);
    }

    @Test
    void confirmPendingFor_锁后状态已非PENDING_跳过不确认() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        SettlementFeeGateway fees = mock(SettlementFeeGateway.class);
        SettlementNavGateway navs = mock(SettlementNavGateway.class);
        PositionCommandHandler positions = mock(PositionCommandHandler.class);
        LedgerEventGateway events = mock(LedgerEventGateway.class);
        RequiresNewTransactionExecutor batchTransactions = mock(RequiresNewTransactionExecutor.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        // 读取批次时仍为 PENDING,锁后已被手动确认提交为 CONFIRMED
        LedgerTransaction confirmedConcurrently = LedgerTransaction.rehydrate(503L, 10L, 1L,
                TransactionSource.INCREASE, TransactionStatus.CONFIRMED, new BigDecimal("100"),
                new BigDecimal("50"), new BigDecimal("2.0"), null, null,
                Instant.parse("2026-07-24T00:00:00Z"), NOW, null, null, null, null, null, null, null);

        when(transactions.findByPortfolioFundAndStatus(10L, TransactionStatus.PENDING))
                .thenReturn(List.of(confirmedConcurrently));
        when(transactions.findByIdForUpdate(503L)).thenReturn(Optional.of(confirmedConcurrently));
        var traded = new TradedPortfolioFundGateway.TradedPortfolioFund(10L, 1L, 31L, 9L, true);
        when(portfolioFunds.findForUpdate(10L)).thenReturn(Optional.of(traded));
        when(portfolioFunds.find(10L)).thenReturn(Optional.of(traded));
        when(fees.feeScheduleOf(31L)).thenReturn(FeeSchedule.none());

        TransactionConfirmationCommandHandler handler = new TransactionConfirmationCommandHandler(
                transactions, lots, portfolioFunds, fees, navs, positions, events, batchTransactions, clock);

        int confirmed = handler.confirmPendingFor(10L, Instant.parse("2026-07-27T00:00:00Z"));

        assertThat(confirmed).isEqualTo(0);
        verify(transactions, never()).save(any());
    }

    @Test
    void confirm_先锁组合基金再锁交易行() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        SettlementFeeGateway fees = mock(SettlementFeeGateway.class);
        SettlementNavGateway navs = mock(SettlementNavGateway.class);
        PositionCommandHandler positions = mock(PositionCommandHandler.class);
        LedgerEventGateway events = mock(LedgerEventGateway.class);
        RequiresNewTransactionExecutor batchTransactions = mock(RequiresNewTransactionExecutor.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LedgerTransaction pending = LedgerTransaction.rehydrate(601L, 10L, 1L, TransactionSource.INCREASE,
                TransactionStatus.PENDING, new BigDecimal("100"), null, null, null, null,
                Instant.parse("2026-07-24T00:00:00Z"), null, null, null, null, null, null, null, null);
        LedgerTransaction confirmed = LedgerTransaction.rehydrate(601L, 10L, 1L, TransactionSource.INCREASE,
                TransactionStatus.CONFIRMED, new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("2.0"),
                null, null, Instant.parse("2026-07-24T00:00:00Z"), NOW, null, null, null, null, null, null, null);
        TradedPortfolioFundGateway.TradedPortfolioFund traded =
                new TradedPortfolioFundGateway.TradedPortfolioFund(10L, 1L, 31L, 9L, true);

        when(transactions.findById(601L)).thenReturn(Optional.of(pending));
        when(transactions.findRelated(601L)).thenReturn(Optional.empty());
        when(transactions.findByIdForUpdate(601L)).thenReturn(Optional.of(pending));
        when(portfolioFunds.findForUpdate(10L)).thenReturn(Optional.of(traded));
        when(portfolioFunds.find(10L)).thenReturn(Optional.of(traded));
        when(fees.feeScheduleOf(31L)).thenReturn(FeeSchedule.none());
        when(navs.unitNavOn(31L, Instant.parse("2026-07-24T00:00:00Z")))
                .thenReturn(Optional.of(new BigDecimal("2.0")));
        when(transactions.save(any(LedgerTransaction.class))).thenReturn(confirmed);

        TransactionConfirmationCommandHandler handler = new TransactionConfirmationCommandHandler(
                transactions, lots, portfolioFunds, fees, navs, positions, events, batchTransactions, clock);

        assertThat(handler.confirm(1L, 601L)).containsExactly(601L);

        InOrder order = inOrder(portfolioFunds, transactions);
        order.verify(portfolioFunds).findForUpdate(10L);
        order.verify(transactions).findByIdForUpdate(601L);
    }

    @Test
    void confirm_锁后按confirmed事实持仓拒绝第二笔卖出() {
        TransactionRepository transactions = mock(TransactionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        TradedPortfolioFundGateway portfolioFunds = mock(TradedPortfolioFundGateway.class);
        SettlementFeeGateway fees = mock(SettlementFeeGateway.class);
        SettlementNavGateway navs = mock(SettlementNavGateway.class);
        PositionCommandHandler positions = mock(PositionCommandHandler.class);
        LedgerEventGateway events = mock(LedgerEventGateway.class);
        RequiresNewTransactionExecutor batchTransactions = mock(RequiresNewTransactionExecutor.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        LedgerTransaction pendingSell = LedgerTransaction.rehydrate(602L, 10L, 1L,
                TransactionSource.DECREASE, TransactionStatus.PENDING, null, new BigDecimal("60"), null,
                null, null, Instant.parse("2026-07-24T00:00:00Z"), null, null, null, null, null, null, null, null);
        LedgerTransaction confirmedAdjustment = LedgerTransaction.rehydrate(700L, 10L, 1L,
                TransactionSource.ADJUST_IN, TransactionStatus.CONFIRMED, null, new BigDecimal("100"), null,
                null, null, Instant.parse("2026-07-23T00:00:00Z"), NOW, null, null, null, null, null, null, null);
        LedgerTransaction confirmedSell = LedgerTransaction.rehydrate(701L, 10L, 1L,
                TransactionSource.DECREASE, TransactionStatus.CONFIRMED, null, new BigDecimal("60"),
                new BigDecimal("2.0"), null, null, Instant.parse("2026-07-24T00:00:00Z"), NOW,
                null, null, null, null, null, null, null);
        TradedPortfolioFundGateway.TradedPortfolioFund traded =
                new TradedPortfolioFundGateway.TradedPortfolioFund(10L, 1L, 31L, 9L, true);

        when(transactions.findById(602L)).thenReturn(Optional.of(pendingSell));
        when(transactions.findByIdForUpdate(602L)).thenReturn(Optional.of(pendingSell));
        when(portfolioFunds.findForUpdate(10L)).thenReturn(Optional.of(traded));
        when(portfolioFunds.find(10L)).thenReturn(Optional.of(traded));
        when(transactions.findByPortfolioFundAndStatus(10L, TransactionStatus.CONFIRMED))
                .thenReturn(List.of(confirmedAdjustment, confirmedSell));
        when(fees.feeScheduleOf(31L)).thenReturn(FeeSchedule.none());
        when(navs.unitNavOn(31L, Instant.parse("2026-07-24T00:00:00Z")))
                .thenReturn(Optional.of(new BigDecimal("2.0")));

        TransactionConfirmationCommandHandler handler = new TransactionConfirmationCommandHandler(
                transactions, lots, portfolioFunds, fees, navs, positions, events, batchTransactions, clock);

        assertThatThrownBy(() -> handler.confirm(1L, 602L))
                .isInstanceOfSatisfying(TransactionConfirmationFailure.class, failure ->
                        assertThat(failure.code()).isEqualTo(
                                TransactionConfirmationFailure.Code.INSUFFICIENT_HOLDING_SHARES));
        verify(transactions, never()).save(any());
        InOrder order = inOrder(portfolioFunds, transactions);
        order.verify(portfolioFunds).findForUpdate(10L);
        order.verify(transactions).findByIdForUpdate(602L);
        order.verify(transactions).findByPortfolioFundAndStatus(10L, TransactionStatus.CONFIRMED);
    }
}
