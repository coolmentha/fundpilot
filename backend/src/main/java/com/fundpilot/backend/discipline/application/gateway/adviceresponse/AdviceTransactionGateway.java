package com.fundpilot.backend.discipline.application.gateway.adviceresponse;

import java.math.BigDecimal;
import java.time.Instant;

/** Advice 回应对 Accounting 创建待确认账目的出站契约。 */
public interface AdviceTransactionGateway {
    PendingTransaction createPending(CreatePending request);

    boolean hasTransaction(long adviceId);

    record CreatePending(long ownerId, long portfolioFundId, Source source, BigDecimal amount,
                         BigDecimal shares, Instant tradeDate, long adviceId) {
    }

    record PendingTransaction(long transactionId) {
    }

    enum Source { INCREASE, DECREASE }

    /** Accounting 的可预期失败已转换为 Discipline 的回应语义。 */
    final class Rejected extends RuntimeException {
        private final boolean alreadyResponded;

        public Rejected(boolean alreadyResponded, String message) {
            super(message);
            this.alreadyResponded = alreadyResponded;
        }

        public boolean alreadyResponded() { return alreadyResponded; }
    }
}
