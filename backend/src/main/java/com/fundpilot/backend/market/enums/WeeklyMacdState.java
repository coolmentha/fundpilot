package com.fundpilot.backend.market.enums;

import com.fundpilot.backend.common.EnumValue;

/**
 * 周 MACD 状态——加仓调节系数表的三个维度之一(见 CONTEXT.md「调节系数表」)。
 * 四值对应框架 §七 周线 MACD 形态:
 * <ul>
 *   <li>{@link #DIVERGENCE_BOTTOM}：底背离,系数 1.2</li>
 *   <li>{@link #GREEN_SHRINKING}：绿柱缩小,系数 1.0</li>
 *   <li>{@link #RED_SHRINKING}：红柱缩小,系数 0.9</li>
 *   <li>{@link #GREEN_EXPANDING}：绿柱扩大,系数 0.6</li>
 * </ul>
 */
public enum WeeklyMacdState implements EnumValue {
    DIVERGENCE_BOTTOM("底背离"),
    GREEN_SHRINKING("绿柱缩小"),
    RED_SHRINKING("红柱缩小"),
    GREEN_EXPANDING("绿柱扩大");

    private final String label;

    WeeklyMacdState(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
