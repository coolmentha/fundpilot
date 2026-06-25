package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;

/**
 * 某基金类型某档的默认纪律参数:回撤阈值(负数,如 -0.08)+ 加仓比例(如 0.15)。
 * <p>回撤阈值用负数与 CONTEXT.md「-8%/-15%」原文一致,净值下跌为负,
 * 加仓判定 {@code drawdown <= defaults.drawdown()} 触发。
 *
 * @param drawdown 回撤阈值(负数 BigDecimal)
 * @param ratio 加仓比例(占 plannedTotalAmount,正数 BigDecimal)
 */
public record TierDefaults(BigDecimal drawdown, BigDecimal ratio) {
}
