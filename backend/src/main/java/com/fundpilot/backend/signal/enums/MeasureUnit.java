package com.fundpilot.backend.signal.enums;

import com.fundpilot.backend.common.EnumValue;

public enum MeasureUnit implements EnumValue {
    SHARE("share"),
    AMOUNT("金额");

    private final String label;

    MeasureUnit(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
