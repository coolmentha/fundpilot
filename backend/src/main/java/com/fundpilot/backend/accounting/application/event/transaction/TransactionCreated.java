package com.fundpilot.backend.accounting.application.event.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/** 账目流水已录入。可重建事件，幂等键为 {@code transactionId}。 */
public record TransactionCreated(long transactionId, long portfolioFundId, long ownerId,
                                 String source, BigDecimal amount, BigDecimal shares,
                                 Instant tradeDate, Instant occurredAt) {
}
