package com.fundpilot.backend.investmentplan.application.gateway.planexecution;

import java.math.BigDecimal;
import java.time.Instant;

/** 定投执行对 Accounting 创建流水的调用方语义。 */
public interface PlanTransactionGateway {
    void createPending(long ownerId, long portfolioFundId, BigDecimal amount, Instant tradeDate, long planId);
    java.util.List<Occurrence> occurrences(long ownerId, Instant startInclusive, Instant endExclusive);
    BigDecimal investedAmount(long ownerId, Instant startInclusive, Instant endExclusive);
    record Occurrence(long planId, Instant tradeDate, BigDecimal amount, String status) {}
    final class AlreadyExecuted extends RuntimeException { public AlreadyExecuted(String message) { super(message); } }
}
