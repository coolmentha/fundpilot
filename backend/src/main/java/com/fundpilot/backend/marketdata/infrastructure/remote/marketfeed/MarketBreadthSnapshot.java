package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

/**
 * 沪深京股票市场宽度快照。
 *
 * @param risingCount  上涨股票家数
 * @param fallingCount 下跌股票家数
 * @param flatCount 平盘股票家数；旧 Redis 快照缺失时为 null
 * @param limitUpCount  涨停股票家数
 * @param limitDownCount 跌停股票家数
 */
public record MarketBreadthSnapshot(
        int risingCount,
        int fallingCount,
        Integer flatCount,
        Integer limitUpCount,
        Integer limitDownCount) {

    /** 东方财富提供涨、跌、平盘家数时的中间结果，不可直接发布。 */
    public MarketBreadthSnapshot(int risingCount, int fallingCount, int flatCount) {
        this(risingCount, fallingCount, flatCount, null, null);
    }

    public boolean isComplete() {
        return flatCount != null && limitUpCount != null && limitDownCount != null;
    }
}
