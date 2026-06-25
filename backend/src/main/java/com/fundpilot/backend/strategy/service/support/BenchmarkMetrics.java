package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;

/**
 * 基准线收益与最大回撤指标(issue #11):策略结果与三条基准线(all-in/DCA/沪深300)共用此结构。
 *
 * @param returnRate  区间收益率
 * @param maxDrawdown 区间最大回撤(非负)
 */
public record BenchmarkMetrics(BigDecimal returnRate, BigDecimal maxDrawdown) {
}
