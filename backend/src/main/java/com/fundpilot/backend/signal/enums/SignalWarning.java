package com.fundpilot.backend.signal.enums;

import com.fundpilot.backend.common.EnumValue;

/**
 * 信号强提示类型(issue #12):加仓信号附属的 warning 分类码。
 * <p>{@link #TIER_CLEARED} 带动态档位后缀(如 {@code TIER_CLEARED:1,2,3}),
 * 由 {@link SignalWarningValue#detail} 承载,拼库时格式化为 {@code name():detail}。
 */
public enum SignalWarning implements EnumValue {
    WEEKLY_COOLDOWN("单周跌幅超阈值"),
    BREAKDOWN_WATCH("破位观察"),
    INSUFFICIENT_DATA_FOR_COOLDOWN("冷却判定数据不足"),
    MIN_HOLD_DAYS_OVERRIDDEN("持有期不足但逻辑止损豁免"),
    TIER_CLEARED("反弹清空档位");

    private final String label;

    SignalWarning(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
