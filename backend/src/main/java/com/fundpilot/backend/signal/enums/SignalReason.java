package com.fundpilot.backend.signal.enums;

import com.fundpilot.backend.common.EnumValue;

/**
 * 信号原因(ADR-0015):定投移动止盈 evaluateSignal 各分支产出的 NONE/SELL 原因码。
 * <p>持久化到 {@code signal_log.reason} 列({@code @Enumerated(EnumType.STRING)}),
 * 枚举 name() 与历史字符串字面量完全一致,存量数据无需迁移。择时路线专属项(LOGIC_BROKEN/REBALANCE/
 * NO_TIER_TO_SELL/NO_ADD_TIER/BUILD_CONDITION_NOT_MET/HARD_CONSTRAINT_BREACH/MIN_HOLD_DAYS_NOT_MET/BUILD/ADD)已随 ADR-0015 废弃。
 */
public enum SignalReason implements EnumValue {
    FUND_CLEARED("基金已清仓"),
    NO_STRATEGY("无生效策略"),
    TRAILING_STOP("移动止盈"),
    EXTREME_MARKET_PROTECT("极端行情保护"),
    FLOOR_REACHED("底仓保留不卖"),
    COOLDOWN_ACTIVE("卖出冷却期内"),
    NOT_YET_ACTIVATED("未达启动门槛"),
    NO_TRIGGER("未触发止盈条件"),
    INSUFFICIENT_MARKET_DATA("行情数据不足");

    private final String label;

    SignalReason(String label) {
        this.label = label;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
