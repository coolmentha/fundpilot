package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 策略视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 *
 * @param id                        策略 ID
 * @param fundId                    基金 ID
 * @param status                    策略状态(PENDING_CALIBRATION/CALIBRATED/EFFECTIVE)
 * @param tier1Drawdown             一档回撤阈值
 * @param tier2Drawdown             二档回撤阈值
 * @param tier3Drawdown             三档回撤阈值
 * @param tier4Drawdown             四档回撤阈值
 * @param tier1Ratio                一档加仓比例
 * @param tier2Ratio                二档加仓比例
 * @param tier3Ratio                三档加仓比例
 * @param tier4Ratio                四档加仓比例
 * @param weeklyCoolDownThreshold   单周跌幅冷却阈值
 * @param stopLossPullbackPercent   移动止盈回撤比例
 * @param tier1AddedAt              一档已加时间
 * @param tier2AddedAt              二档已加时间
 * @param tier3AddedAt              三档已加时间
 * @param tier4AddedAt              四档已加时间
 * @param createdDate               创建时间
 */
public record FundStrategyView(
        Long id,
        Long fundId,
        StrategyParamStatus status,
        BigDecimal tier1Drawdown,
        BigDecimal tier2Drawdown,
        BigDecimal tier3Drawdown,
        BigDecimal tier4Drawdown,
        BigDecimal tier1Ratio,
        BigDecimal tier2Ratio,
        BigDecimal tier3Ratio,
        BigDecimal tier4Ratio,
        BigDecimal weeklyCoolDownThreshold,
        BigDecimal stopLossPullbackPercent,
        Instant tier1AddedAt,
        Instant tier2AddedAt,
        Instant tier3AddedAt,
        Instant tier4AddedAt,
        Instant createdDate) {

    public static FundStrategyView from(FundStrategyEntity strategy) {
        return new FundStrategyView(
                strategy.getId(),
                strategy.getFundEntity() != null ? strategy.getFundEntity().getId() : null,
                strategy.getStatus(),
                strategy.getTier1Drawdown(),
                strategy.getTier2Drawdown(),
                strategy.getTier3Drawdown(),
                strategy.getTier4Drawdown(),
                strategy.getTier1Ratio(),
                strategy.getTier2Ratio(),
                strategy.getTier3Ratio(),
                strategy.getTier4Ratio(),
                strategy.getWeeklyCoolDownThreshold(),
                strategy.getStopLossPullbackPercent(),
                strategy.getTier1AddedAt(),
                strategy.getTier2AddedAt(),
                strategy.getTier3AddedAt(),
                strategy.getTier4AddedAt(),
                strategy.getCreatedDate());
    }
}
