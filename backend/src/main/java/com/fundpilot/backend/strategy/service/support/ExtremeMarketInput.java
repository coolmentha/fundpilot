package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;

/**
 * 极端行情输入(issue #59):单日跌幅与连续3日累计跌幅,供 {@link TrailingStopEngine} 判定极端行情保护。
 * <p>两条加速止盈规则独立于移动止盈线,优先级高于移动止盈:
 * <ul>
 *   <li>单日下跌 ≥ 7% 且已盈利 → 提前卖出 10~20% 仓位</li>
 *   <li>连续 3 个交易日累计跌幅 ≥ 12% → 额外减仓</li>
 * </ul>
 * 调用方(回测模拟器/#62 生产装配)预算后注入;null 表示无极端行情数据(不触发)。
 *
 * @param dailyDropPct          今日 vs 昨日累计净值跌幅(正数小数,如 0.07 表 7%)
 * @param cumulative3DayDropPct 连续3日累计跌幅(正数小数,如 0.12 表 12%)
 */
public record ExtremeMarketInput(BigDecimal dailyDropPct, BigDecimal cumulative3DayDropPct) {

    /** 无极端行情数据(不触发保护)的空输入。 */
    public static ExtremeMarketInput none() {
        return new ExtremeMarketInput(null, null);
    }
}