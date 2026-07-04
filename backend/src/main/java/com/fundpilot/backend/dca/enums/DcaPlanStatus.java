package com.fundpilot.backend.dca.enums;

/**
 * 定投计划状态:DRAFT(草稿,可编辑) → activate → EFFECTIVE(生效,自动下单) → retire → DRAFT。
 */
public enum DcaPlanStatus {
    DRAFT("草稿"),
    EFFECTIVE("生效");

    private final String label;

    DcaPlanStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
