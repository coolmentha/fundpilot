package com.fundpilot.backend.accounting.application.event.position;

import java.time.Instant;

/** 持仓净份额归零转为 CLEARED。关键事件，幂等键为 {@code portfolioFundId + positionVersion}。 */
public record PositionCleared(long portfolioFundId, long ownerId, long positionVersion,
                              Instant occurredAt) {
}
