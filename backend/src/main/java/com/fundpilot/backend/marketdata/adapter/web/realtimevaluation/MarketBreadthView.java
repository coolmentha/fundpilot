package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway.Breadth;

/**
 * 沪深京股票市场宽度视图。
 *
 * @param risingCount  上涨股票家数
 * @param fallingCount 下跌股票家数
 * @param limitUpCount 涨停股票家数
 * @param limitDownCount 跌停股票家数
 */
public record MarketBreadthView(
        int risingCount,
        int fallingCount,
        Integer limitUpCount,
        Integer limitDownCount) {

    public static MarketBreadthView from(Breadth snapshot) {
        return snapshot == null ? null : new MarketBreadthView(
                snapshot.risingCount(), snapshot.fallingCount(),
                snapshot.limitUpCount(), snapshot.limitDownCount());
    }
}
