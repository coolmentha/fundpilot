package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum FundStatus implements EnumValue {
    PENDING_HOLDING("未建仓"),
    HOLDING("持仓中"),
    CLEARED("已清仓");

    private final String label;

    FundStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
