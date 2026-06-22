package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum FundFlowSource implements EnumValue {
    INCREASE("加仓"),
    DECREASE("减仓"),
    TRANSFER_IN("转入"),
    TRANSFER_OUT("转出"),
    INVEST("定投");

    private final String label;

    FundFlowSource(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
