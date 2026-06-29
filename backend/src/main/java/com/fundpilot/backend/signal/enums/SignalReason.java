package com.fundpilot.backend.signal.enums;

import com.fundpilot.backend.common.EnumValue;

/**
 * 信号原因(issue #12):evaluateSignal 各分支产出的 NONE/SELL 原因码。
 * <p>持久化到 {@code signal_log.reason} 列({@code @Enumerated(EnumType.STRING)}),
 * 枚举 name() 与历史字符串字面量完全一致,存量数据无需迁移。
 */
public enum SignalReason implements EnumValue {
    BUILD("建仓"),
    ADD("加仓"),
    FUND_CLEARED("基金已清仓"),
    NO_STRATEGY("无生效策略"),
    BUILD_CONDITION_NOT_MET("建仓条件未满足"),
    NO_ADD_TIER("无加仓档位触发"),
    LOGIC_BROKEN("逻辑止损"),
    NO_TIER_TO_SELL("无可卖档位"),
    TRAILING_STOP("移动止盈"),
    REBALANCE("再平衡减仓"),
    HARD_CONSTRAINT_BREACH("硬约束违反"),
    MIN_HOLD_DAYS_NOT_MET("持有期不足"),
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
