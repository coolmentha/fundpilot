package com.fundpilot.backend.marketdata.domain.indicator;

/** 指数量能状态(20 日均量对比,CONTEXT.md「HIGH_DROP 放量长阴」)。 */
public enum VolumeState {
    HIGH_DROP,
    NORMAL,
    LOW_STABLE
}
