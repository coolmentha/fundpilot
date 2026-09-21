package com.fundpilot.backend.alerting.domain.alertrule;

/** 提醒规则的作用范围。 */
public enum AlertRuleScope {
    /** 全局规则，对该用户全部关注基金生效（已被同类型单基金规则覆盖的基金除外）。 */
    GLOBAL,
    /** 单基金规则，仅对指定基金生效，优先级高于全局规则。 */
    FUND
}
