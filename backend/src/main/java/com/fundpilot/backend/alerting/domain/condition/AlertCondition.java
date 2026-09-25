package com.fundpilot.backend.alerting.domain.condition;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 单条提醒条件：指标 → 关系 → 参数（阈值可省略，省略时取指标默认值）。
 *
 * <p>构造时即完成全部校验并把参数补齐为默认值，因此实例一定处于可直接求值的状态：
 * 未声明的参数、越界取值、指标不支持的关系、必填阈值缺失都会被拒绝。
 */
public record AlertCondition(IndicatorCode indicator, Map<String, Integer> params,
                             ConditionRelation relation, BigDecimal value) {

    public AlertCondition {
        if (indicator == null) {
            throw new IllegalArgumentException("条件指标不能为空");
        }
        if (relation == null) {
            throw new IllegalArgumentException("条件关系不能为空");
        }
        IndicatorRelation spec = indicator.relation(relation);
        params = normalizeParams(indicator, params);
        value = normalizeValue(indicator, spec, value);
    }

    /** 不带参数与阈值的条件，全部取指标默认值。 */
    public static AlertCondition of(IndicatorCode indicator, ConditionRelation relation) {
        return new AlertCondition(indicator, Map.of(), relation, null);
    }

    public static AlertCondition of(IndicatorCode indicator, ConditionRelation relation, BigDecimal value) {
        return new AlertCondition(indicator, Map.of(), relation, value);
    }

    /** 该条件与阈值比较时使用的阈值；放大/缩小类关系不参与阈值比较，返回 null。 */
    public BigDecimal effectiveThreshold() {
        return relation.thresholded() ? value : null;
    }

    private static Map<String, Integer> normalizeParams(IndicatorCode indicator, Map<String, Integer> params) {
        Map<String, Integer> given = params == null ? Map.of() : params;
        for (String name : given.keySet()) {
            if (indicator.parameter(name).isEmpty()) {
                throw new IllegalArgumentException(indicator.label() + "不接受参数: " + name);
            }
        }
        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (IndicatorParameter definition : indicator.parameters()) {
            Integer provided = given.get(definition.name());
            normalized.put(definition.name(),
                    definition.requireInRange(provided == null ? definition.defaultValue() : provided));
        }
        indicator.requireConsistentParams(normalized);
        return Collections.unmodifiableMap(new TreeMap<>(normalized));
    }

    private static BigDecimal normalizeValue(IndicatorCode indicator, IndicatorRelation spec, BigDecimal value) {
        if (!spec.relation().thresholded()) {
            return null;
        }
        BigDecimal threshold = value != null ? value : spec.effectiveDefaultThreshold();
        if (threshold == null) {
            throw new IllegalArgumentException(indicator.label() + "的「" + spec.alias() + "」条件必须填写阈值");
        }
        if (threshold.compareTo(indicator.minimum()) < 0 || threshold.compareTo(indicator.maximum()) > 0) {
            throw new IllegalArgumentException(indicator.label() + "的阈值必须在 " + indicator.minimum() + "~"
                    + indicator.maximum() + " 之间");
        }
        return threshold;
    }
}