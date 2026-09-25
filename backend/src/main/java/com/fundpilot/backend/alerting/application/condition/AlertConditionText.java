package com.fundpilot.backend.alerting.application.condition;

import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import com.fundpilot.backend.alerting.domain.condition.IndicatorRelation;
import java.math.BigDecimal;
import java.util.stream.Collectors;

/**
 * 条件的中文措辞：规则摘要与命中说明。
 *
 * <p>中文文案集中在应用层，领域层只提供指标名与关系别名，保持与展示无关。
 */
public final class AlertConditionText {

    private static final String CONDITION_JOINER = " 且 ";
    private static final String MISSING_VALUE = "当前无可用数据";

    private AlertConditionText() {
    }

    /** 整条规则的条件摘要，如「净值与均线偏离率 下穿均线 且 周线 MACD 柱高 柱较上周放大」。 */
    public static String summarize(ConditionGroup group) {
        return group.conditions().stream().map(AlertConditionText::summarize)
                .collect(Collectors.joining(CONDITION_JOINER));
    }

    /** 单条条件的说明，如「当日涨跌幅 高于 0.05」。 */
    public static String summarize(AlertCondition condition) {
        String base = phrase(condition);
        BigDecimal threshold = condition.effectiveThreshold();
        return threshold == null ? base : base + " " + decimal(threshold);
    }

    /** 单条条件的命中说明，如「当日涨跌幅 高于 0.05，现值 0.0732」。 */
    public static String describe(AlertCondition condition, BigDecimal latest) {
        String base = summarize(condition);
        return latest == null ? base + "（" + MISSING_VALUE + "）" : base + "，现值 " + decimal(latest);
    }

    private static String phrase(AlertCondition condition) {
        IndicatorRelation relation = condition.indicator().relation(condition.relation());
        return condition.indicator().label() + " " + relation.alias();
    }

    private static String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}