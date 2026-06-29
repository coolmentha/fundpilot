package com.fundpilot.backend.fund.service.support;

/**
 * 年线状态——加仓调节系数表三维度之一(见 CONTEXT.md「调节系数表」)。
 * 由 {@code market_indicator_snapshot.price_above_year_line} 与 {@code year_line_rising}
 * 两个布尔组合而成,三态对应系数:上方且向上 1.0 / 上方但向下 0.7 / 下方且向下 0.4。
 */
public enum YearLineState {
    ABOVE_RISING,
    ABOVE_FALLING,
    BELOW_FALLING
}
