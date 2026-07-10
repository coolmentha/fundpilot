package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.enums.FundCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TakeProfitPresetServiceTest {

    private final TakeProfitPresetService service = new TakeProfitPresetService();

    @Test
    void 四类基金返回各自推荐值() {
        assertPreset(FundCategory.BROAD_BASE, "0.15", "0.06", "0.50", "0.50");
        assertPreset(FundCategory.SECTOR, "0.20", "0.08", "0.50", "0.40");
        assertPreset(FundCategory.ACTIVE, "0.15", "0.07", "0.50", "0.50");
        assertPreset(FundCategory.MIXED, "0.12", "0.05", "0.40", "0.60");
    }

    @Test
    void 空请求补齐推荐且不标记自定义() {
        TakeProfitPreset preset = service.recommend(FundCategory.BROAD_BASE);

        StrategyConfigRequest resolved = service.fillMissing(null, preset);

        assertThat(service.isCustomized(resolved, preset)).isFalse();
        assertThat(resolved.maxSingleSellPercent()).isEqualByComparingTo("0.20");
        assertThat(resolved.cooldownTradingDays()).isEqualTo(10);
    }

    @Test
    void 用户修改任一参数后标记自定义() {
        TakeProfitPreset preset = service.recommend(FundCategory.BROAD_BASE);
        StrategyConfigRequest request = new StrategyConfigRequest(
                new BigDecimal("0.18"),
                preset.stopLossPullbackPercent(),
                preset.profitHarvestPercent(),
                preset.minimumHoldingPercent(),
                preset.maxSingleSellPercent(),
                preset.cooldownTradingDays());

        assertThat(service.isCustomized(request, preset)).isTrue();
    }

    private void assertPreset(FundCategory category, String activation, String pullback,
                              String harvest, String minimumHolding) {
        TakeProfitPreset preset = service.recommend(category);
        assertThat(preset.profitActivationPercent()).isEqualByComparingTo(activation);
        assertThat(preset.stopLossPullbackPercent()).isEqualByComparingTo(pullback);
        assertThat(preset.profitHarvestPercent()).isEqualByComparingTo(harvest);
        assertThat(preset.minimumHoldingPercent()).isEqualByComparingTo(minimumHolding);
        assertThat(preset.maxSingleSellPercent()).isEqualByComparingTo("0.20");
        assertThat(preset.cooldownTradingDays()).isEqualTo(10);
    }
}
