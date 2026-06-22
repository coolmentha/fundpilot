package com.fundpilot.backend.fund.enums;

import com.fundpilot.backend.common.EnumValue;

public enum InvestmentPhilosophy implements EnumValue {
    ACTIVE("主动管理"),
    PASSIVE_INDEX("被动指数"),
    ENHANCED_INDEX("指数增强"),
    QUANTITATIVE("量化投资"),
    VALUE("价值投资"),
    GROWTH("成长投资"),
    BALANCED("均衡配置"),
    TARGET_DATE("目标日期"),
    TARGET_RISK("目标风险");

    private final String label;

    InvestmentPhilosophy(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
