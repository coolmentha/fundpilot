package com.fundpilot.backend.alerting.domain.alertrule;

/**
 * 提醒规则的种类：通用条件规则，以及两条从卖出纪律迁移过来的建议型规则。
 *
 * <p>通用条件规则走无状态求值器（{@code ConditionEvaluator}）；建议型规则自带判定与状态机，
 * 结果是「建议卖出多少份额」，仅写入邮件正文，不自动生成交易。
 */
public enum AlertRuleKind {

    /** 通用条件规则：一组指标条件全部满足即提醒。 */
    CONDITION("条件提醒"),
    /** 逻辑破坏止损：条件全部满足时建议全仓卖出（主动型基金豁免量能数据缺失）。 */
    LOGIC_BROKEN("逻辑破坏止损"),
    /** 回撤止盈：收益率达标后按周期峰值回撤吐出浮盈的一部分。 */
    TRAILING_STOP("回撤止盈");

    private final String label;

    AlertRuleKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否为建议型规则：判定不走通用条件求值器，通知内容包含建议卖出份额。 */
    public boolean suggestion() {
        return this != CONDITION;
    }

    /** 是否需要条件数组：回撤止盈的判定由参数与状态机决定。 */
    public boolean needsConditions() {
        return this != TRAILING_STOP;
    }
}