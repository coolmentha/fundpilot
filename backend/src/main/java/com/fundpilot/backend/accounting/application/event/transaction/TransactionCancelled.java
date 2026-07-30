package com.fundpilot.backend.accounting.application.event.transaction;

import java.time.Instant;

/** 账目流水已撤销。关键事件，幂等键为 {@code transactionId + version}。 */
public record TransactionCancelled(long transactionId, long portfolioFundId, long ownerId,
                                   String source, Long signalLogId, Long dcaPlanId,
                                   Long disciplineAdviceId, Long investmentPlanId, Instant cancelledAt,
                                   long version, Instant occurredAt) {
}
