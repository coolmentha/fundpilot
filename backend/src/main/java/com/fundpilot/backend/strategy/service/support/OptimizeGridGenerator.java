package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.service.support.DefaultCoolDownTable;
import com.fundpilot.backend.fund.service.support.DefaultTierTable;
import com.fundpilot.backend.fund.service.support.TierDefaults;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 寻优网格生成纯函数(issue #26):对一只基金按 {@code fundCategory} 从 {@link DefaultTierTable}
 * 取默认回撤阈值为中心,生成 3 维网格——回撤起点(tier1Drawdown)、回撤终点(tier4Drawdown)、
 * 止盈回落(stopLossPullbackPercent),每维 4 档。中间两档(tier2/tier3)按默认表相对位置内插,
 * 保留默认结构先验(CONTEXT.md「寻优搜索维度」)。
 *
 * <p>非搜索维度——4 档加仓比例(15/20/25/30)与 weeklyCoolDownThreshold——用默认值不搜,
 * 减少过拟合维度。四档回撤必须单调递减(更负,即 tier1>tier2>tier3>tier4),
 * 搜出的组合若 tier1 ≤ tier4 则丢弃,避免深档先于浅档触发的逻辑错误。
 *
 * <p>纯函数,不接 API/DB/前端,可独立单测验证。
 *
 * @see OptimizeParams
 */
public final class OptimizeGridGenerator {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 每维搜索档数。 */
    private static final int STEPS_PER_DIM = 4;
    /** 每维以默认值为中心的浮动幅度(pp),4 档取 {-3, -1, +1, +3} pp,步长 2pp。 */
    private static final BigDecimal[] TIER_OFFSETS_PP = {
            new BigDecimal("-0.03"), new BigDecimal("-0.01"), new BigDecimal("0.01"), new BigDecimal("0.03")
    };
    /** 止盈回落搜索档(正数小数,以默认 0.08 为中心 ±3pp,步长 2pp)。 */
    private static final BigDecimal[] STOP_LOSS_OFFSETS = {
            new BigDecimal("-0.03"), new BigDecimal("-0.01"), new BigDecimal("0.01"), new BigDecimal("0.03")
    };
    private static final BigDecimal DEFAULT_STOP_LOSS_PULLBACK = new BigDecimal("0.08");
    private static final BigDecimal[] DEFAULT_RATIOS = {
            new BigDecimal("0.15"), new BigDecimal("0.20"), new BigDecimal("0.25"), new BigDecimal("0.30")
    };

    private OptimizeGridGenerator() {
    }

    /**
     * @param category 基金类型(决定默认回撤阈值中心与 weeklyCoolDownThreshold)
     * @return 满足递增约束的候选参数组合列表(最多 64 组,过滤后更少)
     */
    public static List<OptimizeParams> generate(FundCategory category) {
        TierDefaults d1 = DefaultTierTable.lookup(category, 1);
        TierDefaults d2 = DefaultTierTable.lookup(category, 2);
        TierDefaults d3 = DefaultTierTable.lookup(category, 3);
        TierDefaults d4 = DefaultTierTable.lookup(category, 4);
        BigDecimal defaultTier1 = d1.drawdown();
        BigDecimal defaultTier4 = d4.drawdown();
        // 默认表中间档相对位置:(default_tierN - default_tier1) / (default_tier4 - default_tier1)
        BigDecimal span = defaultTier4.subtract(defaultTier1, MATH);
        BigDecimal ratio2 = ratioBetween(d2.drawdown(), defaultTier1, span);
        BigDecimal ratio3 = ratioBetween(d3.drawdown(), defaultTier1, span);
        BigDecimal defaultCoolDown = DefaultCoolDownTable.lookup(category);

        List<OptimizeParams> grid = new ArrayList<>();
        for (BigDecimal o1 : TIER_OFFSETS_PP) {
            BigDecimal tier1 = defaultTier1.add(o1, MATH);
            for (BigDecimal o4 : TIER_OFFSETS_PP) {
                BigDecimal tier4 = defaultTier4.add(o4, MATH);
                // 递增约束:tier1 > tier4(负数,越浅越大);违反则跳过该 (tier1,tier4) 组合
                if (tier1.compareTo(tier4) <= 0) {
                    continue;
                }
                BigDecimal tier2 = interpolate(tier1, tier4, ratio2);
                BigDecimal tier3 = interpolate(tier1, tier4, ratio3);
                // 内插后仍需满足严格递减(tier1>tier2>tier3>tier4)
                if (!(tier1.compareTo(tier2) > 0 && tier2.compareTo(tier3) > 0 && tier3.compareTo(tier4) > 0)) {
                    continue;
                }
                for (BigDecimal os : STOP_LOSS_OFFSETS) {
                    BigDecimal stopLoss = DEFAULT_STOP_LOSS_PULLBACK.add(os, MATH);
                    if (stopLoss.signum() <= 0) {
                        continue; // 止盈回落须为正
                    }
                    grid.add(new OptimizeParams(
                            tier1, tier2, tier3, tier4, stopLoss,
                            DEFAULT_RATIOS[0], DEFAULT_RATIOS[1], DEFAULT_RATIOS[2], DEFAULT_RATIOS[3],
                            defaultCoolDown, category, null));
                }
            }
        }
        return List.copyOf(grid);
    }

    /** 算某中间档在 tier1→tier4 区间的相对位置 ∈ [0,1]:(tierN - tier1) / (tier4 - tier1)。 */
    private static BigDecimal ratioBetween(BigDecimal tierN, BigDecimal tier1, BigDecimal span) {
        if (span.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return tierN.subtract(tier1, MATH).divide(span, MATH);
    }

    /** 按相对位置 ratio 在 [tier1, tier4] 之间内插:tier1 + ratio × (tier4 - tier1)。 */
    private static BigDecimal interpolate(BigDecimal tier1, BigDecimal tier4, BigDecimal ratio) {
        return tier1.add(tier4.subtract(tier1, MATH).multiply(ratio, MATH), MATH);
    }
}
