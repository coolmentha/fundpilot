package com.fundpilot.backend.market.client;

import java.math.BigDecimal;

/**
 * 赎回费档位(issue #60):持有期达到 {@code holdingDays} 天起,赎回费率为 {@code feeRate}。
 * <p>按持有期升序排列;择档时取 holdingDays ≤ 实际持有期的最深档。典型档位:<7日1.5%、<30日0.75%、<365日0.5%、≥365日0。
 *
 * @param holdingDays 起算持有天数(≥1)
 * @param feeRate     赎回费率(正数小数,如 0.015 表 1.5%);0 表示免赎回费
 */
public record RedemptionFeeTier(int holdingDays, BigDecimal feeRate) {
}