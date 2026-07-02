package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 策略视图 DTO(ADR-0015):只含业务字段,关联对象只取 id。移动止盈参数。
 *
 * @param id                    策略 ID
 * @param fundId                基金 ID
 * @param status                策略状态
 * @param activationThreshold   启动门槛
 * @param pullbackTierCount     回撤分级有效档数
 * @param pullbackTier1Yield    一档起算收益率
 * @param pullbackTier1Ratio    一档回撤比例
 * @param pullbackTier2Yield    二档起算收益率
 * @param pullbackTier2Ratio    二档回撤比例
 * @param pullbackTier3Yield    三档起算收益率
 * @param pullbackTier3Ratio    三档回撤比例
 * @param pullbackTier4Yield    四档起算收益率
 * @param pullbackTier4Ratio    四档回撤比例
 * @param sellRatio             每次卖出比例
 * @param floorRatio            底仓保留比例
 * @param cooldownDays          卖出冷却交易日数
 * @param createdDate           创建时间
 */
public record FundStrategyView(
        Long id,
        Long fundId,
        StrategyParamStatus status,
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
        Integer cooldownDays,
        Instant createdDate) {

    public static FundStrategyView from(FundStrategyEntity strategy) {
        return new FundStrategyView(
                strategy.getId(),
                strategy.getFundEntity() != null ? strategy.getFundEntity().getId() : null,
                strategy.getStatus(),
                strategy.getActivationThreshold(),
                strategy.getPullbackTierCount(),
                strategy.getPullbackTier1Yield(),
                strategy.getPullbackTier1Ratio(),
                strategy.getPullbackTier2Yield(),
                strategy.getPullbackTier2Ratio(),
                strategy.getPullbackTier3Yield(),
                strategy.getPullbackTier3Ratio(),
                strategy.getPullbackTier4Yield(),
                strategy.getPullbackTier4Ratio(),
                strategy.getSellRatio(),
                strategy.getFloorRatio(),
                strategy.getCooldownDays(),
                strategy.getCreatedDate());
    }
}