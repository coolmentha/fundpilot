package com.fundpilot.backend.market.enums;

import com.fundpilot.backend.common.EnumValue;

/**
 * 成交量状态——加仓调节系数表的三个维度之一(见 CONTEXT.md「调节系数表」)。
 * 三值对应框架 §七 量能形态:
 * <ul>
 *   <li>{@link #LOW_STABLE}：地量企稳,系数 1.2</li>
 *   <li>{@link #NORMAL}：正常,系数 1.0</li>
 *   <li>{@link #HIGH_DROP}：放量下跌,系数 0.5</li>
 * </ul>
 */
public enum VolumeState implements EnumValue {
    LOW_STABLE("地量企稳"),
    NORMAL("正常"),
    HIGH_DROP("放量下跌");

    private final String label;

    VolumeState(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
