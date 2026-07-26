package com.fundpilot.backend.accounting.application.event.position;

import java.time.Instant;

/** 持仓由 EMPTY/CLEARED 转为 OPEN。关键事件，幂等键为 {@code portfolioFundId + positionVersion}。 */
public record PositionOpened(long portfolioFundId, long ownerId, Instant openedAt,
                             long positionVersion, Instant occurredAt) {
}
