package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum InvestmentTarget implements EnumValue {
    STOCK("股票型"),
    BOND("债券型"),
    MIXED("混合型"),
    MONEY_MARKET("货币市场型"),
    QDII("合格境内机构投资者"),
    FOF("基金中基金"),
    REIT("不动产投资信托"),
    COMMODITY("商品型"),
    ALTERNATIVE("另类投资");

    private final String label;

    InvestmentTarget(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
