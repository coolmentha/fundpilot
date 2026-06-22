package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum StrategyParamStatus implements EnumValue {
    PENDING_CALIBRATION("待校准"),
    CALIBRATED("已校准"),
    EFFECTIVE("已生效");

    private final String label;

    StrategyParamStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
