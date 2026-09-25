package com.fundpilot.backend.alerting.domain.condition;

import java.util.List;

/** 一组条件的合取：当前仅支持「全部满足」，故本类型只保证条件非空与不可变。 */
public record ConditionGroup(AlertConditionMatch match, List<AlertCondition> conditions) {

    public ConditionGroup {
        if (match == null) {
            throw new IllegalArgumentException("条件组合方式不能为空");
        }
        if (conditions == null || conditions.isEmpty()) {
            throw new IllegalArgumentException("提醒规则至少需要一条条件");
        }
        conditions = List.copyOf(conditions);
    }

    public static ConditionGroup allOf(List<AlertCondition> conditions) {
        return new ConditionGroup(AlertConditionMatch.ALL, conditions);
    }

    public static ConditionGroup single(AlertCondition condition) {
        return allOf(List.of(condition));
    }
}