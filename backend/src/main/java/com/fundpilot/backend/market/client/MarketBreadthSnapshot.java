package com.fundpilot.backend.market.client;

/**
 * 沪深京股票市场宽度快照。
 *
 * @param risingCount  上涨股票家数
 * @param fallingCount 下跌股票家数
 * @param limitUpCount  涨停股票家数
 * @param limitDownCount 跌停股票家数
 */
public record MarketBreadthSnapshot(
        int risingCount,
        int fallingCount,
        Integer limitUpCount,
        Integer limitDownCount) {

    /** 东方财富仅提供上涨、下跌家数时的中间结果，不可直接发布。 */
    public MarketBreadthSnapshot(int risingCount, int fallingCount) {
        this(risingCount, fallingCount, null, null);
    }

    public boolean hasLimitCounts() {
        return limitUpCount != null && limitDownCount != null;
    }
}
