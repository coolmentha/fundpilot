package com.fundpilot.backend.strategy.service;

import java.math.BigDecimal;

/**
 * 策略参数配置请求(ADR-0015):移动止盈参数。对应 {@code FundStrategyEntity} 的移动止盈字段。
 * <p>回撤分级表用至多 4 档 yield/ratio 对(第一档起算收益率对齐启动门槛)。
 *
 * @param activationThreshold   启动门槛(宽基 0.50、行业 0.40)
 * @param pullbackTierCount     回撤分级有效档数(2~4)
 * @param pullbackTier1Yield    一档起算收益率
 * @param pullbackTier1Ratio    一档回撤比例
 * @param pullbackTier2Yield    二档起算收益率
 * @param pullbackTier2Ratio    二档回撤比例
 * @param pullbackTier3Yield    三档起算收益率
 * @param pullbackTier3Ratio    三档回撤比例
 * @param pullbackTier4Yield    四档起算收益率(行业用;宽基可 null)
 * @param pullbackTier4Ratio    四档回撤比例(行业用;宽基可 null)
 * @param sellRatio             每次卖出比例(宽基 0.20、行业 0.25)
 * @param floorRatio            底仓保留比例(宽基 0.40、行业 0.25)
 * @param cooldownDays          卖出冷却交易日数(20)
 */
public record StrategyConfigRequest(
        BigDecimal activationThreshold,
        Integer pullbackTierCount,
        BigDecimal pullbackTier1Yield,
        BigDecimal pullbackTier1Ratio,
        BigDecimal pullbackTier2Yield,
        BigDecimal pullbackTier2Ratio,
        BigDecimal pullbackTier3Yield,
        BigDecimal pullbackTier3Ratio,
        BigDecimal pullbackTier4Yield,
        BigDecimal pullbackTier4Ratio,
        BigDecimal sellRatio,
        BigDecimal floorRatio,
        Integer cooldownDays) {
}