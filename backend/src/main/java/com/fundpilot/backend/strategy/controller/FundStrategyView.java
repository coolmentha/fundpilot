package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 策略视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 * <p>包含定投止盈参数、推荐来源和运行时周期状态。
 *
 * @param id                        策略 ID
 * @param fundId                    基金 ID
 * @param status                    策略状态(PENDING_CALIBRATION 表示草稿；旧校准状态仅兼容存量数据)
 * @param stopLossPullbackPercent   止盈周期高点回撤比例
 * @param createdDate               创建时间
 */
public record FundStrategyView(
        Long id,
        Long fundId,
        StrategyParamStatus status,
        BigDecimal profitActivationPercent,
        BigDecimal stopLossPullbackPercent,
        BigDecimal profitHarvestPercent,
        BigDecimal minimumHoldingPercent,
        BigDecimal maxSingleSellPercent,
        Integer cooldownTradingDays,
        FundCategory presetFundCategory,
        Integer presetVersion,
        boolean customized,
        TakeProfitPhase takeProfitPhase,
        Instant createdDate) {

    public static FundStrategyView from(FundStrategyEntity strategy) {
        return new FundStrategyView(
                strategy.getId(),
                strategy.getFundEntity() != null ? strategy.getFundEntity().getId() : null,
                strategy.getStatus(),
                strategy.getProfitActivationPercent(),
                strategy.getStopLossPullbackPercent(),
                strategy.getProfitHarvestPercent(),
                strategy.getMinimumHoldingPercent(),
                strategy.getMaxSingleSellPercent(),
                strategy.getCooldownTradingDays(),
                strategy.getPresetFundCategory(),
                strategy.getPresetVersion(),
                strategy.isCustomized(),
                strategy.getTakeProfitPhase(),
                strategy.getCreatedDate());
    }
}
