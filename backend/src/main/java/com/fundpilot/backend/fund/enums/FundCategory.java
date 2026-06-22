package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum FundCategory implements EnumValue {
    BROAD_BASE("宽基"),
    SECTOR("行业"),
    ACTIVE("主动"),
    MIXED("混合");

    private final String label;

    FundCategory(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }

}
