package com.fundpilot.backend.dca.controller;

import java.math.BigDecimal;

/** 当前北京时间自然月的定投现金流摘要。金额均为人民币元。 */
public record DcaBudgetSummaryView(
        BigDecimal monthlyBudget,
        BigDecimal investedAmount,
        BigDecimal futureAmount,
        BigDecimal projectedAmount,
        BigDecimal remainingAmount,
        BigDecimal overBudgetAmount) {
}
