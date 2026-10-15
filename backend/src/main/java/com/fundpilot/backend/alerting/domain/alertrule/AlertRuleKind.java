package com.fundpilot.backend.alerting.domain.alertrule;

/**
 * 提醒规则的种类：通用条件规则，两条从卖出纪律迁移过来的建议型规则，以及网格交易提醒。
 *
 * <p>通用条件规则走无状态求值器（{@code ConditionEvaluator}）；建议型规则自带判定，
 * 结果是「建议卖出多少份额」，仅写入邮件正文，不自动生成交易。
 */
public enum AlertRuleKind {

    /** 通用条件规则：一组指标条件全部满足即提醒。 */
    CONDITION("条件提醒"),
    /** 逻辑破坏止损：条件全部满足时建议全仓卖出（主动型基金豁免量能数据缺失）。 */
    LOGIC_BROKEN("逻辑破坏止损"),
    /** 回撤止盈：收益率达标后按周期峰值回撤吐出浮盈的一部分。 */
    TRAILING_STOP("回撤止盈"),
    /** 网格交易提醒：以最近一条成交净值为基准线，盘中估值越线即按方向给出买卖建议。 */
    GRID("网格交易提醒");

    private final String label;

    AlertRuleKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 是否为建议型规则：判定不走通用条件求值器，通知内容包含建议操作。 */
    public boolean suggestion() {
        return this != CONDITION;
    }

    /**
     * 是否需要条件数组：回撤止盈与网格的判定都由参数决定，不配置条件组。
     *
     * <p>注意 {@code LOGIC_BROKEN} 仍需要条件（它的判定就是在条件之上叠加量能口径）。
     */
    public boolean needsConditions() {
        return this != TRAILING_STOP && this != GRID;
    }
}