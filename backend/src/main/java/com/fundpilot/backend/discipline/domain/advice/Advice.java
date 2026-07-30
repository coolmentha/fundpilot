package com.fundpilot.backend.discipline.domain.advice;

import java.time.Instant;
import java.math.BigDecimal;

/** 建议聚合。账目是否已创建由 Accounting 的 advice 幂等键保证。 */
public final class Advice {
    private final long id;
    private final long portfolioFundId;
    private final long ownerId;
    private final AdviceAction action;
    private final Instant signalDate;
    private final Integer triggerTier;
    private final BigDecimal coefficient;
    private final BigDecimal suggestedValue;
    private final String suggestedMeasureUnit;
    private final String reason;
    private final String warnings;
    private Instant ignoredAt;
    private AdviceResponseStatus responseStatus;

    private Advice(long id, long portfolioFundId, long ownerId, AdviceAction action, Instant signalDate,
                   Integer triggerTier, BigDecimal coefficient, BigDecimal suggestedValue, String suggestedMeasureUnit,
                   String reason, String warnings, Instant ignoredAt, AdviceResponseStatus responseStatus) {
        this.id = id;
        this.portfolioFundId = portfolioFundId;
        this.ownerId = ownerId;
        this.action = action;
        this.signalDate = signalDate; this.triggerTier = triggerTier; this.coefficient = coefficient;
        this.suggestedValue = suggestedValue; this.suggestedMeasureUnit = suggestedMeasureUnit;
        this.reason = reason; this.warnings = warnings;
        this.ignoredAt = ignoredAt;
        this.responseStatus = responseStatus;
    }

    public static Advice rehydrate(long id, long portfolioFundId, long ownerId, AdviceAction action,
                                   Instant ignoredAt, AdviceResponseStatus responseStatus) {
        return rehydrate(id, portfolioFundId, ownerId, action, null, null, null, null, null, null, null,
                ignoredAt, responseStatus);
    }
    public static Advice rehydrate(long id, long portfolioFundId, long ownerId, AdviceAction action,
                                   Instant signalDate, Integer triggerTier, BigDecimal coefficient,
                                   BigDecimal suggestedValue, String suggestedMeasureUnit, String reason,
                                   String warnings, Instant ignoredAt, AdviceResponseStatus responseStatus) {
        return new Advice(id, portfolioFundId, ownerId, action, signalDate, triggerTier, coefficient,
                suggestedValue, suggestedMeasureUnit, reason, warnings, ignoredAt, responseStatus);
    }

    public void ignore(Instant occurredAt) {
        if (action == AdviceAction.NONE) {
            throw new IllegalStateException("无建议不可忽略");
        }
        if (ignoredAt == null) {
            ignoredAt = occurredAt;
            responseStatus = AdviceResponseStatus.IGNORED;
        }
    }

    public void markResponded() {
        if (responseStatus != AdviceResponseStatus.IGNORED) {
            responseStatus = AdviceResponseStatus.RESPONDED;
        }
    }

    public void markPending() {
        if (responseStatus == AdviceResponseStatus.RESPONDED) {
            responseStatus = AdviceResponseStatus.PENDING;
        }
    }

    public long id() { return id; }
    public long portfolioFundId() { return portfolioFundId; }
    public long ownerId() { return ownerId; }
    public AdviceAction action() { return action; }
    public Instant signalDate() { return signalDate; } public Integer triggerTier() { return triggerTier; }
    public BigDecimal coefficient() { return coefficient; } public BigDecimal suggestedValue() { return suggestedValue; }
    public String suggestedMeasureUnit() { return suggestedMeasureUnit; } public String reason() { return reason; }
    public String warnings() { return warnings; }
    public Instant ignoredAt() { return ignoredAt; }
    public AdviceResponseStatus responseStatus() { return responseStatus; }
}
