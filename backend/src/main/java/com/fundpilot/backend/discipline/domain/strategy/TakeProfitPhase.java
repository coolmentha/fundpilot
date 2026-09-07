package com.fundpilot.backend.discipline.domain.strategy;

/**
 * 定投止盈运行周期状态机(工作台领域上下文「FundStrategyEntity 运行时周期」):
 * {@code ACCUMULATING --达到启动收益率--> ARMED --markTriggered--> TRIGGERED --确认卖出--> COOLDOWN},
 * 冷静期结束按是否仍达阈值回到 {@code ARMED} 或 {@code ACCUMULATING};忽略在途建议时
 * {@code TRIGGERED --supersedeTriggered--> ARMED}。策略未激活或已退役时为 null。
 */
public enum TakeProfitPhase {
    ACCUMULATING,
    ARMED,
    TRIGGERED,
    COOLDOWN
}
