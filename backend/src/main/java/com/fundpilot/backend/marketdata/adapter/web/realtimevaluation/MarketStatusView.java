package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeMarketOverviewQueryHandler.MarketStatus;
import java.time.Instant;

/** A 股交易状态与行情工作台核心数据时效。 */
public record MarketStatusView(String marketState, Instant updatedAt) {
    public static MarketStatusView from(MarketStatus status) {
        return new MarketStatusView(status.marketState().name(), status.updatedAt());
    }
}
