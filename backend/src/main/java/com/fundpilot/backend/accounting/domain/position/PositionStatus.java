package com.fundpilot.backend.accounting.domain.position;

/**
 * 持仓状态，由 CONFIRMED 账本推导，与组合基金有效性正交。
 *
 * <pre>
 * 无 CONFIRMED 交易                 -> EMPTY
 * CONFIRMED 净份额 &gt; 0              -> OPEN
 * 已有 CONFIRMED 交易且净份额 &lt;= 0   -> CLEARED
 * CLEARED 后确认正向交易             -> OPEN
 * </pre>
 */
public enum PositionStatus {
    EMPTY,
    OPEN,
    CLEARED
}
