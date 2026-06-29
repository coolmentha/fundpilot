package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 默认回撤档位表:宽基/行业/主动/混合 × 四档的默认回撤阈值 + 默认加仓比例。
 * <p>四类基金加仓比例统一 15/20/25/30%(金字塔递增,合计 90%),差异化只体现在回撤阈值上
 * (CONTEXT.md「四档加仓比例」「仓位结构」)。回撤阈值照 issue #5 规格与框架 §四 落表,
 * 负数表示跌幅幅度。
 * <p>无状态静态查表,信号引擎与状态机据此识别档位与计算加仓额。
 */
public final class DefaultTierTable {

    private static final Map<FundCategory, BigDecimal[]> DRAWDOWNS = Map.of(
            FundCategory.BROAD_BASE, new BigDecimal[]{new BigDecimal("-0.08"), new BigDecimal("-0.15"), new BigDecimal("-0.25"), new BigDecimal("-0.35")},
            FundCategory.SECTOR, new BigDecimal[]{new BigDecimal("-0.15"), new BigDecimal("-0.25"), new BigDecimal("-0.35"), new BigDecimal("-0.45")},
            FundCategory.ACTIVE, new BigDecimal[]{new BigDecimal("-0.12"), new BigDecimal("-0.20"), new BigDecimal("-0.30"), new BigDecimal("-0.40")},
            FundCategory.MIXED, new BigDecimal[]{new BigDecimal("-0.12"), new BigDecimal("-0.22"), new BigDecimal("-0.32"), new BigDecimal("-0.40")}
    );

    private static final BigDecimal[] RATIOS = {
            new BigDecimal("0.15"), new BigDecimal("0.20"), new BigDecimal("0.25"), new BigDecimal("0.30")
    };

    private DefaultTierTable() {
    }

    /**
     * @param category 基金类型
     * @param tier 档位 1~4
     * @return 该类型该档的回撤阈值与加仓比例
     */
    public static TierDefaults lookup(FundCategory category, int tier) {
        if (tier < 1 || tier > 4) {
            throw new IllegalArgumentException("tier 必须是 1~4,实际: " + tier);
        }
        BigDecimal drawdown = DRAWDOWNS.get(category)[tier - 1];
        BigDecimal ratio = RATIOS[tier - 1];
        return new TierDefaults(drawdown, ratio);
    }
}
