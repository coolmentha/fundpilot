package com.fundpilot.backend.market.client;

/** 同花顺大盘统计中的最新涨停、跌停家数。 */
public record MarketLimitCounts(int limitUpCount, int limitDownCount) {
}
