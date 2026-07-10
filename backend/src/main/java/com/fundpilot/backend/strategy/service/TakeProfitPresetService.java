package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.enums.FundCategory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

/** 按基金类型提供定投止盈推荐值，所有消费者共用这一份模板。 */
@Service
public class TakeProfitPresetService {

    private static final int PRESET_VERSION = 1;
    private static final BigDecimal MAX_SINGLE_SELL = new BigDecimal("0.20");
    private static final int COOLDOWN_DAYS = 10;

    private static final Map<FundCategory, TakeProfitPreset> PRESETS = createPresets();

    public TakeProfitPreset recommend(FundCategory category) {
        if (category == null) {
            throw ErrorCode.FUND_CATEGORY_REQUIRED.toException("基金类型为空，无法推荐定投止盈参数");
        }
        return PRESETS.get(category);
    }

    public StrategyConfigRequest fillMissing(StrategyConfigRequest request, TakeProfitPreset preset) {
        StrategyConfigRequest source = request != null ? request : preset.toRequest();
        return new StrategyConfigRequest(
                valueOrDefault(source.profitActivationPercent(), preset.profitActivationPercent()),
                valueOrDefault(source.stopLossPullbackPercent(), preset.stopLossPullbackPercent()),
                valueOrDefault(source.profitHarvestPercent(), preset.profitHarvestPercent()),
                valueOrDefault(source.minimumHoldingPercent(), preset.minimumHoldingPercent()),
                valueOrDefault(source.maxSingleSellPercent(), preset.maxSingleSellPercent()),
                source.cooldownTradingDays() != null ? source.cooldownTradingDays() : preset.cooldownTradingDays());
    }

    public boolean isCustomized(StrategyConfigRequest request, TakeProfitPreset preset) {
        return request.profitActivationPercent().compareTo(preset.profitActivationPercent()) != 0
                || request.stopLossPullbackPercent().compareTo(preset.stopLossPullbackPercent()) != 0
                || request.profitHarvestPercent().compareTo(preset.profitHarvestPercent()) != 0
                || request.minimumHoldingPercent().compareTo(preset.minimumHoldingPercent()) != 0
                || request.maxSingleSellPercent().compareTo(preset.maxSingleSellPercent()) != 0
                || !request.cooldownTradingDays().equals(preset.cooldownTradingDays());
    }

    private static BigDecimal valueOrDefault(BigDecimal value, BigDecimal defaultValue) {
        return value != null ? value : defaultValue;
    }

    private static Map<FundCategory, TakeProfitPreset> createPresets() {
        Map<FundCategory, TakeProfitPreset> presets = new EnumMap<>(FundCategory.class);
        presets.put(FundCategory.BROAD_BASE, preset(FundCategory.BROAD_BASE, "0.15", "0.06", "0.50", "0.50"));
        presets.put(FundCategory.SECTOR, preset(FundCategory.SECTOR, "0.20", "0.08", "0.50", "0.40"));
        presets.put(FundCategory.ACTIVE, preset(FundCategory.ACTIVE, "0.15", "0.07", "0.50", "0.50"));
        presets.put(FundCategory.MIXED, preset(FundCategory.MIXED, "0.12", "0.05", "0.40", "0.60"));
        return Map.copyOf(presets);
    }

    private static TakeProfitPreset preset(FundCategory category, String activation, String pullback,
                                           String harvest, String minimumHolding) {
        return new TakeProfitPreset(
                category,
                PRESET_VERSION,
                new BigDecimal(activation),
                new BigDecimal(pullback),
                new BigDecimal(harvest),
                new BigDecimal(minimumHolding),
                MAX_SINGLE_SELL,
                COOLDOWN_DAYS);
    }
}
