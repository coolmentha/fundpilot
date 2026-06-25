package com.fundpilot.backend.fund.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 默认单周跌幅冷静阈值表:宽基 8% / 行业 12% / 主动 10% / 混合 10%
 * (CONTEXT.md「单周跌幅冷静」)。
 * <p>加仓信号专属强提示阈值,超过即在加仓信号 warnings 加 WEEKLY_COOLDOWN,不阻断加仓。
 * 阈值用正数小数表示跌幅幅度。
 */
public final class DefaultCoolDownTable {

    private static final Map<FundCategory, BigDecimal> THRESHOLDS = Map.of(
            FundCategory.BROAD_BASE, new BigDecimal("0.08"),
            FundCategory.SECTOR, new BigDecimal("0.12"),
            FundCategory.ACTIVE, new BigDecimal("0.10"),
            FundCategory.MIXED, new BigDecimal("0.10")
    );

    private DefaultCoolDownTable() {
    }

    public static BigDecimal lookup(FundCategory category) {
        return THRESHOLDS.get(category);
    }
}
