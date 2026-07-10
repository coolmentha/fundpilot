package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.MarketBreadthSnapshot;

/**
 * 沪深京股票市场宽度视图。
 *
 * @param risingCount  上涨股票家数
 * @param fallingCount 下跌股票家数
 */
public record MarketBreadthView(
        int risingCount,
        int fallingCount) {

    public static MarketBreadthView from(MarketBreadthSnapshot snapshot) {
        return snapshot == null ? null : new MarketBreadthView(
                snapshot.risingCount(), snapshot.fallingCount());
    }
}
