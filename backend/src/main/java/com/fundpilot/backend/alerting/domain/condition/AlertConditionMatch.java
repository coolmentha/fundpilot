package com.fundpilot.backend.alerting.domain.condition;

/** 条件组合方式。当前仅支持「全部满足」，保留该字段以便后续扩展而无需改存储结构。 */
public enum AlertConditionMatch {

    /** 全部条件都满足时触发。 */
    ALL
}