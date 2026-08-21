package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.sharedkernel.enums.EnumValue;

public enum FundTransactionSource implements EnumValue {
    INCREASE("加仓"),
    DECREASE("减仓"),
    TRANSFER_IN("转入"),
    TRANSFER_OUT("转出"),
    INVEST("定投"),
    ADJUST_IN("调增"),
    ADJUST_OUT("调减"),
    COST_BASIS_RESET("成本修正");

    private final String label;

    FundTransactionSource(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
