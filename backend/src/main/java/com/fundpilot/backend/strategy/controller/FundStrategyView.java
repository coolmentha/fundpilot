package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 策略视图 DTO(issue #16):只含业务字段,关联对象只取 id,不暴露 Entity 内部字段。
 * <p>金字塔加仓机制移除后,只剩移动止盈阈值(stopLossPullbackPercent)与版本状态机(status)。
 *
 * @param id                        策略 ID
 * @param fundId                    基金 ID
 * @param status                    策略状态(PENDING_CALIBRATION/CALIBRATED/EFFECTIVE)
 * @param stopLossPullbackPercent   移动止盈回撤比例(回落 n×本阈值卖 holdingShares×n/4)
 * @param createdDate               创建时间
 */
public record FundStrategyView(
        Long id,
        Long fundId,
        StrategyParamStatus status,
        BigDecimal stopLossPullbackPercent,
        Instant createdDate) {

    public static FundStrategyView from(FundStrategyEntity strategy) {
        return new FundStrategyView(
                strategy.getId(),
                strategy.getFundEntity() != null ? strategy.getFundEntity().getId() : null,
                strategy.getStatus(),
                strategy.getStopLossPullbackPercent(),
                strategy.getCreatedDate());
    }
}
