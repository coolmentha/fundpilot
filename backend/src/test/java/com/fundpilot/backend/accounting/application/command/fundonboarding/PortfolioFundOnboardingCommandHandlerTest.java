package com.fundpilot.backend.accounting.application.command.fundonboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.accounting.application.gateway.fundonboarding.FundGroupingGateway;
import com.fundpilot.backend.accounting.application.gateway.fundonboarding.InitialPositionNavGateway;
import com.fundpilot.backend.accounting.application.gateway.fundonboarding.OnboardedPortfolioFundGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.position.PositionStatus;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import com.fundpilot.backend.accounting.domain.transaction.TransactionSource;
import com.fundpilot.backend.accounting.domain.transaction.TransactionStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PortfolioFundOnboardingCommandHandlerTest {

    @Test
    void onboard_reusedClearedFundWithoutSharesRequiresRepurchaseShares() {
        OnboardedPortfolioFundGateway portfolioFunds = mock(OnboardedPortfolioFundGateway.class);
        PositionRepository positionFacts = mock(PositionRepository.class);
        PortfolioFundOnboardingCommandHandler handler = new PortfolioFundOnboardingCommandHandler(
                portfolioFunds, mock(FundGroupingGateway.class), mock(InitialPositionNavGateway.class),
                mock(TransactionRepository.class), mock(LotRepository.class), positionFacts,
                mock(PositionCommandHandler.class), mock(LedgerEventGateway.class),
                Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC));
        when(portfolioFunds.track(41L, 3L, 9L, true, new BigDecimal("0.30")))
                .thenReturn(new OnboardedPortfolioFundGateway.OnboardedPortfolioFund(11L, 3L, 9L, true));
        when(positionFacts.findByPortfolioFund(11L)).thenReturn(Optional.of(
                Position.rehydrate(1L, 0L, 11L, 3L, PositionStatus.CLEARED, null, null)));

        assertThatThrownBy(() -> handler.onboard(41L, 3L, 9L, true, new BigDecimal("0.30"),
                null, null, null))
                .isInstanceOfSatisfying(PortfolioFundOnboardingFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo(
                            PortfolioFundOnboardingFailure.Code.PORTFOLIO_FUND_ALREADY_TRACKED);
                    assertThat(failure.getMessage()).contains("回购时请填写本次份额");
                });
    }

    @Test
    void onboard_reusedClearedFundRejectsRepurchaseBeforeLatestTransaction() {
        OnboardedPortfolioFundGateway portfolioFunds = mock(OnboardedPortfolioFundGateway.class);
        PositionRepository positionFacts = mock(PositionRepository.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        PortfolioFundOnboardingCommandHandler handler = new PortfolioFundOnboardingCommandHandler(
                portfolioFunds, mock(FundGroupingGateway.class), mock(InitialPositionNavGateway.class),
                transactions, mock(LotRepository.class), positionFacts,
                mock(PositionCommandHandler.class), mock(LedgerEventGateway.class),
                Clock.fixed(Instant.parse("2026-07-03T00:00:00Z"), ZoneOffset.UTC));
        when(portfolioFunds.track(41L, 3L, 9L, true, new BigDecimal("0.30")))
                .thenReturn(new OnboardedPortfolioFundGateway.OnboardedPortfolioFund(11L, 3L, 9L, true));
        when(positionFacts.findByPortfolioFund(11L)).thenReturn(Optional.of(
                Position.rehydrate(1L, 0L, 11L, 3L, PositionStatus.CLEARED, null, null)));
        when(transactions.findByPortfolioFundAndStatus(11L, TransactionStatus.CONFIRMED))
                .thenReturn(List.of(LedgerTransaction.rehydrate(101L, 11L, 3L,
                        TransactionSource.DECREASE, TransactionStatus.CONFIRMED, null, BigDecimal.ONE,
                        BigDecimal.ONE, null, null, Instant.parse("2026-07-02T00:00:00Z"),
                        Instant.parse("2026-07-02T00:00:00Z"), null,
                        Instant.parse("2026-07-02T00:00:00Z"), null, null, null)));

        assertThatThrownBy(() -> handler.onboard(41L, 3L, 9L, true, new BigDecimal("0.30"),
                BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-07-01T00:00:00Z")))
                .isInstanceOfSatisfying(PortfolioFundOnboardingFailure.class, failure -> {
                    assertThat(failure.code()).isEqualTo(
                            PortfolioFundOnboardingFailure.Code.OPENED_AT_BEFORE_LAST_TRANSACTION);
                    assertThat(failure.getMessage()).contains("不能早于");
                });
    }

    @Test
    void onboard_withoutSharesRejectsCostOrOpenedAtBeforeCreatingPortfolioFund() {
        OnboardedPortfolioFundGateway portfolioFunds = mock(OnboardedPortfolioFundGateway.class);
        PortfolioFundOnboardingCommandHandler handler = new PortfolioFundOnboardingCommandHandler(
                portfolioFunds, mock(FundGroupingGateway.class), mock(InitialPositionNavGateway.class),
                mock(TransactionRepository.class),
                mock(LotRepository.class), mock(PositionRepository.class), mock(PositionCommandHandler.class), mock(LedgerEventGateway.class),
                Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> handler.onboard(41L, 3L, 9L, true, new BigDecimal("0.30"),
                null, new BigDecimal("1.10"), null))
                .isInstanceOfSatisfying(PortfolioFundOnboardingFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(PortfolioFundOnboardingFailure.Code.INITIAL_HOLDING_SHARES_INVALID));

        verifyNoInteractions(portfolioFunds);
    }

    @Test
    void onboard_rejectsNonPositiveSharesBeforeCreatingPortfolioFund() {
        OnboardedPortfolioFundGateway portfolioFunds = mock(OnboardedPortfolioFundGateway.class);
        PortfolioFundOnboardingCommandHandler handler = new PortfolioFundOnboardingCommandHandler(
                portfolioFunds, mock(FundGroupingGateway.class), mock(InitialPositionNavGateway.class),
                mock(TransactionRepository.class),
                mock(LotRepository.class), mock(PositionRepository.class), mock(PositionCommandHandler.class), mock(LedgerEventGateway.class),
                Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> handler.onboard(41L, 3L, 9L, true, new BigDecimal("0.30"),
                BigDecimal.ZERO, null, null))
                .isInstanceOfSatisfying(PortfolioFundOnboardingFailure.class,
                        failure -> assertThat(failure.code())
                                .isEqualTo(PortfolioFundOnboardingFailure.Code.INITIAL_HOLDING_SHARES_INVALID));

        verifyNoInteractions(portfolioFunds);
    }

    @Test
    void onboard_清仓回购创建新账目并恢复持仓() {
        OnboardedPortfolioFundGateway portfolioFunds = mock(OnboardedPortfolioFundGateway.class);
        InitialPositionNavGateway navs = mock(InitialPositionNavGateway.class);
        TransactionRepository transactions = mock(TransactionRepository.class);
        LotRepository lots = mock(LotRepository.class);
        PositionRepository positionFacts = mock(PositionRepository.class);
        PositionCommandHandler positions = mock(PositionCommandHandler.class);
        LedgerEventGateway events = mock(LedgerEventGateway.class);
        FundGroupingGateway groups = mock(FundGroupingGateway.class);
        Instant openedAt = Instant.parse("2026-07-01T00:00:00Z");
        PortfolioFundOnboardingCommandHandler handler = new PortfolioFundOnboardingCommandHandler(
                portfolioFunds, groups, navs, transactions, lots,
                positionFacts, positions, events,
                Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC));
        when(portfolioFunds.track(41L, 3L, 9L, true, new BigDecimal("0.30")))
                .thenReturn(new OnboardedPortfolioFundGateway.OnboardedPortfolioFund(11L, 3L, 9L, true));
        when(positionFacts.findByPortfolioFund(11L)).thenReturn(Optional.of(
                Position.rehydrate(1L, 0L, 11L, 3L, PositionStatus.CLEARED, null, null)));
        when(transactions.findByPortfolioFundAndStatus(11L, TransactionStatus.CONFIRMED))
                .thenReturn(List.of());
        when(navs.latest(9L)).thenReturn(Optional.of(new InitialPositionNavGateway.PublishedNav(
                openedAt, new BigDecimal("1.2000"))));
        when(transactions.save(any())).thenReturn(LedgerTransaction.rehydrate(101L, 11L, 3L,
                TransactionSource.INCREASE, TransactionStatus.CONFIRMED, new BigDecimal("55.00000050"),
                new BigDecimal("50.00"), new BigDecimal("1.2000"), null, null, openedAt, openedAt,
                null, openedAt, null, null, null));

        var result = handler.onboard(41L, 3L, 9L, true, new BigDecimal("0.30"),
                new BigDecimal("50"), new BigDecimal("1.100000009"), openedAt);

        assertThat(result.portfolioFundId()).isEqualTo(11L);
        assertThat(result.initialTransactionId()).isEqualTo(101L);
        ArgumentCaptor<LedgerTransaction> created = ArgumentCaptor.forClass(LedgerTransaction.class);
        verify(transactions).save(created.capture());
        assertThat(created.getValue().amount()).isEqualByComparingTo("55.00000050");
        ArgumentCaptor<Lot> lot = ArgumentCaptor.forClass(Lot.class);
        verify(lots).save(lot.capture());
        assertThat(lot.getValue().acquireTransactionId()).isEqualTo(101L);
        assertThat(lot.getValue().acquireCostPerShare()).isEqualByComparingTo("1.10000001");
        verify(positions).applyExistingPosition(11L, 3L, new BigDecimal("1.10000001"), openedAt);
        ArgumentCaptor<TransactionConfirmed> confirmed = ArgumentCaptor.forClass(TransactionConfirmed.class);
        verify(events).publishConfirmed(confirmed.capture());
        assertThat(confirmed.getValue().transactionId()).isEqualTo(101L);
        assertThat(confirmed.getValue().amount()).isEqualByComparingTo("55.00000050");
        verifyNoInteractions(groups);
    }

    @Test
    void onboard_rejectsCostsThatRoundToZeroOrExceedDatabasePrecision() {
        OnboardedPortfolioFundGateway portfolioFunds = mock(OnboardedPortfolioFundGateway.class);
        PortfolioFundOnboardingCommandHandler handler = new PortfolioFundOnboardingCommandHandler(
                portfolioFunds, mock(FundGroupingGateway.class), mock(InitialPositionNavGateway.class),
                mock(TransactionRepository.class), mock(LotRepository.class), mock(PositionRepository.class), mock(PositionCommandHandler.class),
                mock(LedgerEventGateway.class),
                Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC));

        for (BigDecimal cost : List.of(new BigDecimal("0.000000001"),
                new BigDecimal("100000000000.00000000"))) {
            assertThatThrownBy(() -> handler.onboard(null, 3L, 9L, true, new BigDecimal("0.30"),
                    new BigDecimal("10"), cost, null))
                    .isInstanceOfSatisfying(PortfolioFundOnboardingFailure.class,
                            failure -> assertThat(failure.code())
                                    .isEqualTo(PortfolioFundOnboardingFailure.Code.COST_PER_SHARE_INVALID));
        }
        verifyNoInteractions(portfolioFunds);
    }
}
