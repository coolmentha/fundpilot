package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 全局硬约束常量(CONTEXT.md「总仓位硬约束」「7 天内不赎回硬约束」与 issue #5 规格)。
 * <p>硬约束管"主动加仓不能突破上限",与再平衡减仓(管"存量超限被动卖出")是两套机制。
 * {@link #TIER_CLEAR_BUFFER} 见 ADR-0003,反弹清空判定侧 0.5% 缓冲带,写死不可调。
 */
public final class HardConstraintConfig {

    /** 建仓首笔比例:建仓额 = plannedTotalAmount × 0.10(CONTEXT.md「建仓首笔比例」)。 */
    public static final BigDecimal BUILD_RATIO = new BigDecimal("0.10");

    /** 反弹清空缓冲带 0.5%(ADR-0003),只在清空侧加,加档侧精确触发。 */
    public static final BigDecimal TIER_CLEAR_BUFFER = new BigDecimal("0.005");

    /** 单类基金总仓位上限 30%。 */
    public static final BigDecimal CATEGORY_POSITION_LIMIT = new BigDecimal("0.30");

    /** 总权益仓位上限 80%(分母为 UserConfig.totalInvestableCapital)。 */
    public static final BigDecimal TOTAL_EQUITY_POSITION_LIMIT = new BigDecimal("0.80");

    /** 单次加仓比例上限 50%(防止单次加仓过猛)。 */
    public static final BigDecimal SINGLE_ADD_RATIO_LIMIT = new BigDecimal("0.50");

    /** 加仓档位数(金字塔四档,CONTEXT.md「四档加仓比例」)。 */
    public static final int TIER_COUNT = 4;

    /** 持有期最少交易日数(非自然日,贴近市场节奏)。 */
    public static final int MIN_HOLD_DAYS = 5;

    private static final Map<FundCategory, BigDecimal> SINGLE_POSITION_LIMITS = Map.of(
            FundCategory.BROAD_BASE, new BigDecimal("0.20"),
            FundCategory.SECTOR, new BigDecimal("0.15"),
            FundCategory.ACTIVE, new BigDecimal("0.20"),
            FundCategory.MIXED, new BigDecimal("0.20")
    );

    private HardConstraintConfig() {
    }

    /** 单只基金仓位上限:宽基/主动/混合 20%,行业 15%(CONTEXT.md「再平衡减仓」)。 */
    public static BigDecimal singlePositionLimit(FundCategory category) {
        return SINGLE_POSITION_LIMITS.get(category);
    }
}
