package com.fundpilot.backend.fund.client;

import java.math.BigDecimal;

/**
 * 赎回费率阶梯一档。
 *
 * @param maxDays 持有天数上限(自然日),{@code null} 表示「≥上一档上限」(最后一档,如 ≥730天)
 * @param rate    赎回费率(小数,如 0.005 表 0.5%)
 */
public record RedemptionTier(
        Integer maxDays,
        BigDecimal rate
) {}
