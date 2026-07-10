package com.fundpilot.backend.strategy.service;

import java.math.BigDecimal;

/**
 * 定投止盈策略配置请求。百分比统一使用正数比例。
 */
public record StrategyConfigRequest(
        BigDecimal profitActivationPercent,
        BigDecimal stopLossPullbackPercent,
        BigDecimal profitHarvestPercent,
        BigDecimal minimumHoldingPercent,
        BigDecimal maxSingleSellPercent,
        Integer cooldownTradingDays) {
}
