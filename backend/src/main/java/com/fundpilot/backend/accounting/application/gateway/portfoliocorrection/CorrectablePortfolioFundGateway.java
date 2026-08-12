package com.fundpilot.backend.accounting.application.gateway.portfoliocorrection;

import java.time.Instant;
import java.util.Optional;

public interface CorrectablePortfolioFundGateway {
    Optional<PortfolioFund> findOwned(long ownerId, long portfolioFundId);

    Optional<PortfolioFund> findOwnedForUpdate(long ownerId, long portfolioFundId);

    VoidResult voidPortfolioFund(long ownerId, long portfolioFundId, long actorId,
                                 String reason, Instant occurredAt);

    final class Rejected extends RuntimeException {
        private final Reason reason;

        public Rejected(Reason reason, String message, Throwable cause) {
            super(message, cause);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    enum Reason {
        NOT_FOUND,
        INVALID_REASON,
        CONFLICT
    }

    record PortfolioFund(long id, Long legacyFundId, Validity validity,
                         Instant voidedAt, Long voidedBy, String voidReason) {
    }

    record VoidResult(long id, boolean changed, Instant voidedAt,
                      Long voidedBy, String voidReason) {
    }

    enum Validity {
        TRACKED,
        VOIDED
    }
}
