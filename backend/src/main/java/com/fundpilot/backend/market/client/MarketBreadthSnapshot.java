package com.fundpilot.backend.market.client;

/**
 * 沪深京股票市场宽度快照。
 *
 * @param risingCount  上涨股票家数
 * @param fallingCount 下跌股票家数
 */
public record MarketBreadthSnapshot(
        int risingCount,
        int fallingCount) {
}
