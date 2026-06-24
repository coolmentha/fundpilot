package com.fundpilot.backend.signal.valueobject;

import com.fundpilot.backend.signal.enums.MeasureUnit;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 通用的「带单位量值」值对象——一个数值配它的量纲单位。
 * 本期只在 {@code SignalLogEntity.suggestedMeasure} 使用(BUILD/ADD 时存建议金额、SELL 时存建议份额)。
 */
@Embeddable
public class Measure implements Serializable {
    private final BigDecimal value;

    @Enumerated(EnumType.STRING)
    private final MeasureUnit measureUnit;

    public Measure(BigDecimal value, MeasureUnit measureUnit) {
        this.value = value;
        this.measureUnit = measureUnit;
    }

    // JPA 要求无参构造器(用于实体化时反射实例化),字段在构造后由 Hibernate 反射写入。
    protected Measure() {
        this.value = null;
        this.measureUnit = null;
    }
}
