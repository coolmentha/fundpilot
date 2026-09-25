package com.fundpilot.backend.alerting.domain.condition;

import java.math.BigDecimal;

/**
 * 指标可选的某个关系：中文别名与默认阈值。
 *
 * <p>默认阈值为空时，若该关系需要阈值（{@code ABOVE}/{@code BELOW}）则用户必须填写；事件型关系
 * （上穿/下穿）的隐含界线为 0，无需用户填写。
 */
public record IndicatorRelation(ConditionRelation relation, String alias, BigDecimal defaultValue) {

    public IndicatorRelation {
        if (relation == null) {
            throw new IllegalArgumentException("条件关系不能为空");
        }
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("条件关系别名不能为空");
        }
        alias = alias.trim();
    }

    /** 使用关系自身的通用别名，默认阈值留给指标声明。 */
    public static IndicatorRelation of(ConditionRelation relation, BigDecimal defaultValue) {
        return new IndicatorRelation(relation, relation.alias(), defaultValue);
    }

    /** 使用关系自身的通用别名，且用户必须填写阈值。 */
    public static IndicatorRelation of(ConditionRelation relation) {
        return of(relation, null);
    }

    /** 使用更贴合业务的别名（如「金叉」「柱较上周放大」）。 */
    public static IndicatorRelation named(ConditionRelation relation, String alias, BigDecimal defaultValue) {
        return new IndicatorRelation(relation, alias, defaultValue);
    }

    /** 用户未填阈值时生效的阈值；为空表示该关系必须由用户填写阈值。 */
    public BigDecimal effectiveDefaultThreshold() {
        return defaultValue != null ? defaultValue : relation.implicitThreshold();
    }
}