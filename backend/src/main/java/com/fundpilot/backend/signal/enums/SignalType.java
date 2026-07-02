package com.fundpilot.backend.signal.enums;

import com.fundpilot.backend.common.EnumValue;

/**
 * 信号类型(ADR-0015):收敛为两值。定投买入不经信号引擎(定时任务自动扣款),信号引擎只管止盈卖出。
 */
public enum SignalType implements EnumValue {
    NONE("无建议"),
    SELL("卖出");

    private final String label;

    SignalType(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}

