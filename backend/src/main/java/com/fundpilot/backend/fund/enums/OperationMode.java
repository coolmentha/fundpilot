package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum OperationMode implements EnumValue {
    OPEN_END("开放式"),
    CLOSED_END("封闭式"),
    REGULAR_OPEN("定期开放"),
    MINIMUM_HOLDING_PERIOD("持有期"),
    ETF("交易型开放式指数基金"),
    LOF("上市型开放式基金");

    private final String label;

    OperationMode(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
