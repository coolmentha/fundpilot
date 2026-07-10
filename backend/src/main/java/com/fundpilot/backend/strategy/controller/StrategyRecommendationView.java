package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.strategy.service.TakeProfitPreset;

import java.math.BigDecimal;

/** 基于基金类型生成的定投止盈推荐值。 */
public record StrategyRecommendationView(
        FundCategory fundCategory,
        int presetVersion,
        BigDecimal profitActivationPercent,
        BigDecimal stopLossPullbackPercent,
        BigDecimal profitHarvestPercent,
        BigDecimal minimumHoldingPercent,
        BigDecimal maxSingleSellPercent,
        int cooldownTradingDays) {

    public static StrategyRecommendationView from(TakeProfitPreset preset) {
        return new StrategyRecommendationView(
                preset.fundCategory(),
                preset.presetVersion(),
                preset.profitActivationPercent(),
                preset.stopLossPullbackPercent(),
                preset.profitHarvestPercent(),
                preset.minimumHoldingPercent(),
                preset.maxSingleSellPercent(),
                preset.cooldownTradingDays());
    }
}
