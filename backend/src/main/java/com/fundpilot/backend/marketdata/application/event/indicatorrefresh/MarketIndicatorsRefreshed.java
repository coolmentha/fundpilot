package com.fundpilot.backend.marketdata.application.event.indicatorrefresh;

import java.time.Instant;

/** Signals that the final daily market-indicator batch has completed. */
public record MarketIndicatorsRefreshed(Instant occurredAt) {
}
