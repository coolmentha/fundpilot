package com.fundpilot.backend.marketdata.domain.indicator;

/** 周线 MACD 状态(由累计净值周序列计算,CONTEXT.md「逻辑止损」)。 */
public enum WeeklyMacdState {
    RED_EXPANDING,
    RED_SHRINKING,
    GREEN_EXPANDING,
    GREEN_SHRINKING
}
