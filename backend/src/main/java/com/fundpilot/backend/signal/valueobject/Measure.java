package com.fundpilot.backend.signal.valueobject;

import com.fundpilot.backend.signal.enums.MeasureUnit;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.math.BigDecimal;
@Embeddable
public class Measure implements Serializable {
    private final BigDecimal value;

    private final MeasureUnit measureUnit;

    public Measure(BigDecimal value, MeasureUnit measureUnit){
        this.value = value;
        this.measureUnit = measureUnit;
    }
}
