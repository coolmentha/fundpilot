package com.fundpilot.backend.market.client;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单日基金净值快照:东方财富 pingzhongdata.js {@code Data_netWorthTrend}(单位净值)+
 * {@code Data_ACWorthTrend}(累计净值) 解析结果。
 *
 * @param navDate         净值日期
 * @param nav             单位净值
 * @param accumulatedNav  累计净值
 */
public record FundNavSnapshot(LocalDate navDate, BigDecimal nav, BigDecimal accumulatedNav) {
}