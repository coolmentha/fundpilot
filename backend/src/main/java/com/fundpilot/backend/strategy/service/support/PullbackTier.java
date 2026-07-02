package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;

/**
 * 回撤分级表的一档(issue #57):历史最高收益率达到 {@code minYield} 起,回撤比例用 {@code ratio}。
 * <p>按 {@code minYield} 升序排列,择档时取 {@code minYield <= peakYield} 的最深档。
 * 分级表第一档起点对齐启动门槛(宽基 50%、行业 40%)。
 *
 * @param minYield 该档起算的历史最高收益率(正数小数,如 0.50 表 50%)
 * @param ratio    回撤比例(正数小数,如 0.15 表 15%)
 */
public record PullbackTier(BigDecimal minYield, BigDecimal ratio) {
}
