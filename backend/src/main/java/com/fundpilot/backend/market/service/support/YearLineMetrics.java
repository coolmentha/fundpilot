package com.fundpilot.backend.market.service.support;

import java.math.BigDecimal;

/**
 * 年线指标计算结果。
 *
 * @param yearLineNav        今日 250 日累计净值均线值
 * @param priceAboveYearLine 最新累计净值是否高于年线
 * @param yearLineRising     今日均线是否高于昨日均线(年线向上)
 */
public record YearLineMetrics(BigDecimal yearLineNav, boolean priceAboveYearLine, boolean yearLineRising) {
}
