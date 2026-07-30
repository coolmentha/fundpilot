package com.fundpilot.backend.discipline.domain.strategy;

import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import java.math.BigDecimal;

/** Versioned strategy defaults for a final Discipline classification. */
public record DisciplineStrategyPreset(
        DisciplineCategory category,
        int version,
        BigDecimal profitActivationPercent,
        BigDecimal stopLossPullbackPercent,
        BigDecimal profitHarvestPercent,
        BigDecimal minimumHoldingPercent,
        BigDecimal maxSingleSellPercent,
        int cooldownTradingDays) {

    public static DisciplineStrategyPreset recommend(DisciplineCategory category) {
        return switch (category) {
            case BROAD_BASE -> preset(category, "0.15", "0.06", "0.50", "0.50");
            case SECTOR -> preset(category, "0.20", "0.08", "0.50", "0.40");
            case ACTIVE -> preset(category, "0.15", "0.07", "0.50", "0.50");
            case MIXED -> preset(category, "0.12", "0.05", "0.40", "0.60");
        };
    }

    private static DisciplineStrategyPreset preset(DisciplineCategory category, String activation,
                                                     String pullback, String harvest, String minimumHolding) {
        return new DisciplineStrategyPreset(category, 1, new BigDecimal(activation), new BigDecimal(pullback),
                new BigDecimal(harvest), new BigDecimal(minimumHolding), new BigDecimal("0.20"), 10);
    }
}
