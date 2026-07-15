package com.fundpilot.backend.market.client;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 单日基金净值快照:东方财富 pingzhongdata.js {@code Data_netWorthTrend}(单位净值)+
 * {@code Data_ACWorthTrend}(累计净值) 解析结果。
 *
 * @param navDate         北京时间净值自然日对应的 UTC 00:00 日期标签
 * @param nav             单位净值
 * @param accumulatedNav  累计净值
 */
public record FundNavSnapshot(Instant navDate, BigDecimal nav, BigDecimal accumulatedNav) {
}
