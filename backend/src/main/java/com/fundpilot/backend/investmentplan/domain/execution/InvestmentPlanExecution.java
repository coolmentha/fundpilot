package com.fundpilot.backend.investmentplan.domain.execution;

import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy;
import java.math.BigDecimal;
import java.time.Instant;

public record InvestmentPlanExecution(Long id, long planId, Instant businessDate,
                                      InvestmentPlanAmountStrategy amountStrategy, String ruleVersion,
                                      Result result, String reasonCode, String reason, BigDecimal baseAmount,
                                      BigDecimal actualAmount, BigDecimal deductionRate, Instant dataDate,
                                      String referenceIndexCode, Integer movingAverageDays,
                                      BigDecimal primaryMetric, BigDecimal secondaryMetric) {
    public enum Result { EXECUTED, SKIPPED }
}
