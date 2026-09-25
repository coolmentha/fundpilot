package com.fundpilot.backend.alerting.domain.suggestion;

/** 回撤止盈的阶段；与旧纪律策略的状态机同名同义。 */
public enum TakeProfitPhase {

    /** 累计中：收益率尚未达到启动门槛。 */
    ACCUMULATING,
    /** 已就位：收益率达标并已记录周期峰值，等待回撤。 */
    ARMED,
    /** 已触发：已发出建议（或已提醒），等待进入冷静期。 */
    TRIGGERED,
    /** 冷静期：触发后一段时间内不再提醒。 */
    COOLDOWN
}