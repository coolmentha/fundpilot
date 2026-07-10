package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;

/** 某类基金的定投止盈推荐参数。 */
public record TakeProfitPreset(
        FundCategory fundCategory,
        int presetVersion,
        BigDecimal profitActivationPercent,
        BigDecimal stopLossPullbackPercent,
        BigDecimal profitHarvestPercent,
        BigDecimal minimumHoldingPercent,
        BigDecimal maxSingleSellPercent,
        int cooldownTradingDays) {

    public StrategyConfigRequest toRequest() {
        return new StrategyConfigRequest(
                profitActivationPercent,
                stopLossPullbackPercent,
                profitHarvestPercent,
                minimumHoldingPercent,
                maxSingleSellPercent,
                cooldownTradingDays);
    }
}
