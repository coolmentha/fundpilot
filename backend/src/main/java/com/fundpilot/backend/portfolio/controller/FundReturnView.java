package com.fundpilot.backend.portfolio.controller;

import com.fundpilot.backend.fund.enums.FundStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record FundReturnView(
        Long fundId,
        String fundCode,
        String fundName,
        FundStatus status,
        BigDecimal investedAmount,
        BigDecimal redeemedAmount,
        BigDecimal feeAmount,
        BigDecimal holdingAmount,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalReturn,
        BigDecimal returnRate,
        boolean realizedComplete,
        Instant valuationDate) {
}
