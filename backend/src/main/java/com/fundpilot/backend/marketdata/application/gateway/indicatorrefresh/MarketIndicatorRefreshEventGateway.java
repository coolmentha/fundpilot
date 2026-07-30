package com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.event.indicatorrefresh.MarketIndicatorsRefreshed;

/** Publishes completion of the daily indicator-refresh capability. */
public interface MarketIndicatorRefreshEventGateway {
    void publishMarketIndicatorsRefreshed(MarketIndicatorsRefreshed event);
}
