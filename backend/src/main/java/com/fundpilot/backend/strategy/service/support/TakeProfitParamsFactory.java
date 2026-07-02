package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 {@link FundStrategyEntity} 装配 {@link TakeProfitParams}(issue #57/#62)。
 * <p>回撤分级表从 pullbackTierN_* 字段按 pullbackTierCount 取有效档;按 {@link FundCategory} 选默认值兜底。
 */
public final class TakeProfitParamsFactory {

    private TakeProfitParamsFactory() {
    }

    /** 从策略实体字段装配;fundCategory 用于缺失时兜底默认。 */
    public static TakeProfitParams from(FundStrategyEntity strategy, FundCategory fundCategory) {
        TakeProfitParams defaults = fundCategory == FundCategory.SECTOR
                ? TakeProfitParams.sectorDefaults()
                : TakeProfitParams.broadDefaults();
        if (strategy == null) {
            return defaults;
        }
        BigDecimal threshold = orDefault(strategy.getActivationThreshold(), defaults.activationThreshold());
        BigDecimal sellRatio = orDefault(strategy.getSellRatio(), defaults.sellRatio());
        BigDecimal floorRatio = orDefault(strategy.getFloorRatio(), defaults.floorRatio());
        int cooldown = strategy.getCooldownDays() != null ? strategy.getCooldownDays() : defaults.cooldownDays();
        List<PullbackTier> tiers = buildTiers(strategy, defaults.pullbackTiers());
        return new TakeProfitParams(threshold, tiers, sellRatio, floorRatio, cooldown);
    }

    private static List<PullbackTier> buildTiers(FundStrategyEntity s, List<PullbackTier> defaults) {
        Integer count = s.getPullbackTierCount();
        if (count == null || count <= 0) {
            return defaults;
        }
        List<PullbackTier> tiers = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            BigDecimal y = tier(s, i, true);
            BigDecimal r = tier(s, i, false);
            if (y == null || r == null) {
                break;
            }
            tiers.add(new PullbackTier(y, r));
        }
        return tiers.isEmpty() ? defaults : List.copyOf(tiers);
    }

    private static BigDecimal tier(FundStrategyEntity s, int i, boolean yield) {
        return switch (i) {
            case 1 -> yield ? s.getPullbackTier1Yield() : s.getPullbackTier1Ratio();
            case 2 -> yield ? s.getPullbackTier2Yield() : s.getPullbackTier2Ratio();
            case 3 -> yield ? s.getPullbackTier3Yield() : s.getPullbackTier3Ratio();
            case 4 -> yield ? s.getPullbackTier4Yield() : s.getPullbackTier4Ratio();
            default -> null;
        };
    }

    private static BigDecimal orDefault(BigDecimal val, BigDecimal def) {
        return val != null ? val : def;
    }
}