package com.fundpilot.backend.signal.enums;

/** 信号当前的用户操作状态，仅作动态投影，不重复持久化交易事实。 */
public enum SignalActionStatus {
    INFORMATIONAL,
    PENDING,
    RESPONDED,
    IGNORED,
    EXPIRED
}
