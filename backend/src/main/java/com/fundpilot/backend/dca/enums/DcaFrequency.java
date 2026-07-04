package com.fundpilot.backend.dca.enums;

/**
 * 定投频率。
 */
public enum DcaFrequency {
    DAILY("日定投"),
    WEEKLY("周定投"),
    MONTHLY("月定投");

    private final String label;

    DcaFrequency(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
