package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;

/** 当前交易日可供纯信号引擎使用的定投止盈计算结果。 */
public record TakeProfitEvaluation(
        boolean evaluationEnabled,
        BigDecimal floatingProfit,
        BigDecimal matureRedeemableShares) {

    public static TakeProfitEvaluation disabled() {
        return new TakeProfitEvaluation(false, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
