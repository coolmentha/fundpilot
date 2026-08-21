package com.fundpilot.backend.accounting.domain.transaction;

import java.math.BigDecimal;

/** 账目流水来源；同时决定份额方向与确认流程。 */
public enum TransactionSource {
    INCREASE,
    DECREASE,
    TRANSFER_IN,
    TRANSFER_OUT,
    INVEST,
    ADJUST_IN,
    ADJUST_OUT,
    COST_BASIS_RESET;

    /** 买入类：录入金额，确认时按净值折算份额并建 lot。 */
    public boolean isBuy() {
        return this == INCREASE || this == TRANSFER_IN || this == INVEST;
    }

    /** 卖出类：录入份额，确认时 FIFO 消耗 lot 并计赎回费。 */
    public boolean isSell() {
        return this == DECREASE || this == TRANSFER_OUT;
    }

    /** 调整类：录入即确认，不计净值与费用，只改事实份额。 */
    public boolean isAdjustment() {
        return this == ADJUST_IN || this == ADJUST_OUT;
    }

    /** 成本基准重置只记录成本事实，不改变持仓份额。 */
    public boolean isCostBasisReset() {
        return this == COST_BASIS_RESET;
    }

    /** 份额方向：加仓类 +1，减仓类 -1，成本基准重置 0。 */
    public BigDecimal direction() {
        return switch (this) {
            case INCREASE, TRANSFER_IN, INVEST, ADJUST_IN -> BigDecimal.ONE;
            case DECREASE, TRANSFER_OUT, ADJUST_OUT -> BigDecimal.ONE.negate();
            case COST_BASIS_RESET -> BigDecimal.ZERO;
        };
    }
}
