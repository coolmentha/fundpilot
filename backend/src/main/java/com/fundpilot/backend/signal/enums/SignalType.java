package com.fundpilot.backend.signal.enums;

import com.fundpilot.backend.common.EnumValue;

public enum SignalType implements EnumValue {
    NONE("无建议"),
    BUILD("建仓"),
    ADD("加仓"),
    SELL("卖出");

    private final String label;

    SignalType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}

