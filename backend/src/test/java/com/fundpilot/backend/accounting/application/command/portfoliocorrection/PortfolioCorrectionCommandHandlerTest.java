package com.fundpilot.backend.accounting.application.command.portfoliocorrection;

import com.fundpilot.backend.accounting.application.gateway.portfoliocorrection.CorrectablePortfolioFundGateway;
import com.fundpilot.backend.accounting.domain.transaction.PendingTransactionRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortfolioCorrectionCommandHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T10:00:00Z");

    @Test
    void requiresExplicitConfirmation() {
        var handler = handler(new FakeGateway(), legacyFundId -> false);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, "代码错误", false),
                PortfolioCorrectionFailure.Code.VOID_CONFIRMATION_REQUIRED);
    }

    @Test
    void requiresReason() {
        var handler = handler(new FakeGateway(), legacyFundId -> false);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, " ", true),
                PortfolioCorrectionFailure.Code.VOID_REASON_REQUIRED);
    }

    @Test
    void hidesPortfolioFundOwnedByAnotherUser() {
        var handler = handler(new FakeGateway(), legacyFundId -> false);

        assertFailure(() -> handler.voidPortfolioFund(4L, 11L, "代码错误", true),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    @Test
    void blocksTrackedPortfolioFundWithPendingTransactions() {
        var handler = handler(new FakeGateway(), legacyFundId -> true);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, "代码错误", true),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_HAS_PENDING_TRANSACTIONS);
    }

    @Test
    void voidsOnceAndKeepsFirstAuditOnRetry() {
        FakeGateway gateway = new FakeGateway();
        var handler = handler(gateway, legacyFundId -> false);

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
        var handler = handler(gateway, legacyFundId -> false);

        assertFailure(() -> handler.voidPortfolioFund(3L, 11L, "代码错误", true),
                PortfolioCorrectionFailure.Code.PORTFOLIO_FUND_NOT_FOUND);
    }

    private PortfolioCorrectionCommandHandler handler(
            CorrectablePortfolioFundGateway gateway,
            PendingTransactionRepository pendingTransactions) {
        return new PortfolioCorrectionCommandHandler(gateway, pendingTransactions,
                Clock.fixed(NOW, ZoneOffset.UTC));
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
