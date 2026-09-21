package com.fundpilot.backend.alerting.domain.alertrule;

/** 提醒规则的触发类型。 */
public enum AlertRuleType {
    /** 当日涨跌幅达到阈值时触发。 */
    RISE,
    /** 当日涨跌幅跌破负阈值时触发。 */
    DROP,
    /** 持仓收益率达到阈值时触发（仅对已持仓基金生效）。 */
    PROFIT
}
