package com.fundpilot.backend.accounting.application.command.portfoliocorrection;

import com.fundpilot.backend.accounting.application.gateway.portfoliocorrection.CorrectablePortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.position.Position;
import com.fundpilot.backend.accounting.domain.position.PositionRepository;
import com.fundpilot.backend.accounting.domain.transaction.PendingTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioCorrectionCommandHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @Test
    void requiresExplicitConfirmation() {
        var handler = handler(new FakeGateway(), (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, "代码错误", false),
                PortfolioCorrectionFailure.Code.VOID_CONFIRMATION_REQUIRED);
    }

    @Test
    void requiresReason() {
        var handler = handler(new FakeGateway(), (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, " ", true),
                PortfolioCorrectionFailure.Code.VOID_REASON_REQUIRED);
    }

    @Test
    void hidesPortfolioFundOwnedByAnotherUser() {
        var handler = handler(new FakeGateway(), (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.voidPortfolioFund(4L, 11L, "代码错误", true),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    @Test
    void blocksTrackedPortfolioFundWithPendingTransactions() {
        var handler = handler(new FakeGateway(), (portfolioFundId, legacyFundId) -> true);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, "代码错误", true),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS);
    }

    @Test
    void voidsOnceAndKeepsFirstAuditOnRetry() {
        FakeGateway gateway = new FakeGateway();
        var handler = handler(gateway, (portfolioFundId, legacyFundId) -> false);

        var first = handler.voidPortfolioFund(3L, 11L, " 代码错误 ", true);
        var retry = handler.voidPortfolioFund(3L, 11L, "不同原因", true);

        assertThat(first.changed()).isTrue();
        assertThat(retry.changed()).isFalse();
        assertThat(retry.voidedAt()).isEqualTo(NOW);
        assertThat(retry.voidedBy()).isEqualTo(3L);
        assertThat(retry.voidReason()).isEqualTo("代码错误");
    }

    @Test
    void mapsConcurrentTargetRejectionToAccountingFailure() {
        FakeGateway gateway = new FakeGateway();
        gateway.rejection = new CorrectablePortfolioFundGateway.Rejected(
                CorrectablePortfolioFundGateway.Reason.NOT_FOUND,
                "目标模块中的组合基金已变化", null);
        var handler = handler(gateway, (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, "代码错误", true),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    @Test
    void correctsCurrentCostWithoutChangingOtherPositionFacts() {
        PositionRepository positions = mock(PositionRepository.class);
        Position position = openPosition();
        when(positions.findByPortfolioFund(11L)).thenReturn(Optional.of(position));
        when(positions.save(any(Position.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var handler = handler(new FakeGateway(), positions,
                (portfolioFundId, legacyFundId) -> false);

        var result = handler.correctCostPerShare(3L, 11L, new BigDecimal("1.25"));

        assertThat(result.costPerShare()).isEqualByComparingTo("1.25");
        assertThat(position.openedAt()).isEqualTo(NOW);
        verify(positions).save(position);
    }

    @Test
    void rejectsInvalidCostBeforeLoadingPosition() {
        PositionRepository positions = mock(PositionRepository.class);
        var handler = handler(new FakeGateway(), positions,
                (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.correctCostPerShare(3L, 11L, BigDecimal.ZERO),
                PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID);
    }

    @Test
    void rejectsPositiveCostThatRoundsToZeroAtStorageScale() {
        PositionRepository positions = mock(PositionRepository.class);
        var handler = handler(new FakeGateway(), positions,
                (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.correctCostPerShare(3L, 11L, new BigDecimal("0.000000004")),
                PortfolioCorrectionFailure.Code.COST_PER_SHARE_INVALID);
    }

    @Test
    void rejectsCostCorrectionWhenPositionIsNotOpen() {
        PositionRepository positions = mock(PositionRepository.class);
        when(positions.findByPortfolioFund(11L)).thenReturn(Optional.of(Position.empty(11L, 3L)));
        var handler = handler(new FakeGateway(), positions,
                (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.correctCostPerShare(3L, 11L, new BigDecimal("1.25")),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_OPEN);
    }

    @Test
    void hidesCostCorrectionForAnotherUser() {
        var handler = handler(new FakeGateway(), mock(PositionRepository.class),
                (portfolioFundId, legacyFundId) -> false);

        assertFailure(() -> handler.correctCostPerShare(4L, 11L, new BigDecimal("1.25")),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    private PortfolioCorrectionCommandHandler handler(
            CorrectablePortfolioFundGateway gateway,
            PendingTransactionRepository pendingTransactions) {
        return handler(gateway, mock(PositionRepository.class), pendingTransactions);
    }

    private PortfolioCorrectionCommandHandler handler(
            CorrectablePortfolioFundGateway gateway,
            PositionRepository positions,
            PendingTransactionRepository pendingTransactions) {
        return new PortfolioCorrectionCommandHandler(gateway, positions, pendingTransactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Position openPosition() {
        Position position = Position.empty(11L, 3L);
        position.reconcile(true, new BigDecimal("100"), NOW);
        position.applyExistingPosition(new BigDecimal("1.10"), NOW);
        return position;
    }

    private void assertFailure(Runnable action, PortfolioCorrectionFailure.Code expectedCode) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(PortfolioCorrectionFailure.class,
                        failure -> assertThat(failure.code()).isEqualTo(expectedCode));
    }

    private static final class FakeGateway implements CorrectablePortfolioFundGateway {
        private Validity validity = Validity.TRACKED;
        private Instant voidedAt;
        private Long voidedBy;
        private String voidReason;
        private CorrectablePortfolioFundGateway.Rejected rejection;

        @Override
        public Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId) {
            if (ownerId != 3L || portfolioFundId != 11L) return Optional.empty();
            return Optional.of(new PortfolioFund(
                    11L, 101L, validity, voidedAt, voidedBy, voidReason));
        }

        @Override
        public Optional<PortfolioFund> findOwnedForUpdate(long ownerId, long portfolioFundId) {
            return findOwned(ownerId, portfolioFundId);
        }

        @Override
        public VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                            String reason, Instant occurredAt) {
            if (rejection != null) throw rejection;
            if (validity == Validity.VOIDED) {
                return new VoidResult(portfolioFundId, false, voidedAt, voidedBy, voidReason);
            }
            validity = Validity.VOIDED;
            voidedAt = occurredAt;
            voidedBy = actorId;
            voidReason = reason;
            return new VoidResult(portfolioFundId, true, voidedAt, voidedBy, voidReason);
        }
    }
}
