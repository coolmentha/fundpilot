package com.fundpilot.backend.accounting.application.event.transaction;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 账目流水已确认，携带固化的净值与费用快照。
 * 关键事件，幂等键为 {@code transactionId + version}。
 */
public record TransactionConfirmed(long transactionId, long portfolioFundId, long ownerId,
                                   String source, BigDecimal amount, BigDecimal shares,
                                   BigDecimal nav, BigDecimal fee, Instant tradeDate,
                                   Instant confirmedAt, Long signalLogId, Long dcaPlanId,
                                   long version, Instant occurredAt) {
}
