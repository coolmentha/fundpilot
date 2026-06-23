package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum FundTransactionStatus implements EnumValue {
    PENDING("待确认"),
    CONFIRMED("已确认"),
    CANCELLED("已取消");

    private final String label;

    FundTransactionStatus(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
