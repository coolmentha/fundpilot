package com.fundpilot.backend.discipline.application.gateway.adviceresponse;

import java.math.BigDecimal;
import java.time.Instant;

/** Advice 回应对 Accounting 创建待确认账目的出站契约。 */
public interface AdviceTransactionGateway {
    PendingTransaction createPending(CreatePending request);

    BigDecimal confirmedHoldingShares(long ownerId, long portfolioFundId);

    boolean hasTransaction(long adviceId);

    /** 回应该建议后生成的账目（若有），供建议视图展示交易跳转。 */
    java.util.Optional<RelatedTransaction> relatedTransaction(long adviceId);

    record CreatePending(long ownerId, long portfolioFundId, Source source, BigDecimal amount,
                         BigDecimal shares, Instant tradeDate, long adviceId, String signalReason) {
    }

    record PendingTransaction(long transactionId) {
    }

    record RelatedTransaction(long transactionId, Status status) {
    }

    /** Accounting 账目状态(TransactionApi.Status 同名)。 */
    enum Status { PENDING, CONFIRMED, CANCELLED }

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
