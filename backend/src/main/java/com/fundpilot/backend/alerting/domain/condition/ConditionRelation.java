package com.fundpilot.backend.alerting.domain.condition;

import java.math.BigDecimal;

/**
 * 条件关系：把指标取值归一为「与阈值比较」或「相邻两点之间的关系」。
 *
 * <p>所有指标都被归一到一条标量序列（净值与均线偏离率、MACD 柱高、量能比……），因此关系集合与指标种类
 * 正交：新增指标无需新增关系，而金叉/死叉这类业务叫法由 {@link IndicatorRelation#alias()} 覆盖。
 */
public enum ConditionRelation {

    /** 取值不低于阈值。 */
    ABOVE("高于", true, false),
    /** 取值不高于阈值。 */
    BELOW("低于", true, false),
    /** 上穿阈值：前值不高于阈值且当前值高于阈值。 */
    CROSS_ABOVE("上穿", true, true),
    /** 下穿阈值：前值不低于阈值且当前值低于阈值。 */
    CROSS_BELOW("下穿", true, true),
    /** 较前一个取值放大。 */
    INCREASING("较前值放大", false, true),
    /** 较前一个取值缩小。 */
    DECREASING("较前值缩小", false, true);

    private final String alias;
    private final boolean thresholded;
    private final boolean needsPrevious;

    ConditionRelation(String alias, boolean thresholded, boolean needsPrevious) {
        this.alias = alias;
        this.thresholded = thresholded;
        this.needsPrevious = needsPrevious;
    }

    /** 关系的中文别名，指标可覆盖为更贴合业务的叫法（如 MACD 的「金叉」）。 */
    public String alias() {
        return alias;
    }

    /** 是否与阈值比较；放大/缩小类只比较相邻两点，不接受阈值。 */
    public boolean thresholded() {
        return thresholded;
    }

    /** 是否需要前一个取值（事件型与放大/缩小类关系）。 */
    public boolean needsPrevious() {
        return needsPrevious;
    }

    /** 事件型关系未显式给阈值时的隐含界线：上穿/下穿均以 0 为界。 */
    public BigDecimal implicitThreshold() {
        return thresholded && needsPrevious ? BigDecimal.ZERO : null;
    }
}