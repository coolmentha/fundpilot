package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * 移动止盈参数(issue #57,ADR-0015):按 {@code fundCategory} 分派两套,差异化体现在风险特征。
 *
 * @param activationThreshold 启动门槛:收益率达此值才开始追踪止盈线(宽基 0.50、行业 0.40)
 * @param pullbackTiers       回撤比例分级表(按 minYield 升序);收益越高容忍回撤越深,避免高收益被正常波动洗出
 * @param sellRatio           每次卖出占当前持仓的比例(宽基 0.20、行业 0.25)
 * @param floorRatio          底仓保留比例(宽基 0.40、行业 0.25);累计最多卖 1−floorRatio
 * @param cooldownDays        卖出冷却交易日数(20):卖出后该天数内不再判定止盈
 */
public record TakeProfitParams(
        BigDecimal activationThreshold,
        List<PullbackTier> pullbackTiers,
        BigDecimal sellRatio,
        BigDecimal floorRatio,
        int cooldownDays) {

    /** 宽基默认参数:启动门槛 50%、3 档回撤、每次卖 20%、保留 40% 底仓、冷却 20 交易日。 */
    public static TakeProfitParams broadDefaults() {
        return new TakeProfitParams(
                new BigDecimal("0.50"),
                List.of(
                        new PullbackTier(new BigDecimal("0.50"), new BigDecimal("0.15")),
                        new PullbackTier(new BigDecimal("0.80"), new BigDecimal("0.18")),
                        new PullbackTier(new BigDecimal("1.50"), new BigDecimal("0.20"))),
                new BigDecimal("0.20"),
                new BigDecimal("0.40"),
                20);
    }

    /** 行业默认参数:启动门槛 40%、4 档回撤、每次卖 25%、保留 25% 底仓、冷却 20 交易日。 */
    public static TakeProfitParams sectorDefaults() {
        return new TakeProfitParams(
                new BigDecimal("0.40"),
                List.of(
                        new PullbackTier(new BigDecimal("0.40"), new BigDecimal("0.10")),
                        new PullbackTier(new BigDecimal("0.60"), new BigDecimal("0.12")),
                        new PullbackTier(new BigDecimal("0.90"), new BigDecimal("0.14")),
                        new PullbackTier(new BigDecimal("1.30"), new BigDecimal("0.17"))),
                new BigDecimal("0.25"),
                new BigDecimal("0.25"),
                20);
    }
}
