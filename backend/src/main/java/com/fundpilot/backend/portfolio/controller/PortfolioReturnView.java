package com.fundpilot.backend.portfolio.controller;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioReturnView(
        BigDecimal investedAmount,
        BigDecimal redeemedAmount,
        BigDecimal feeAmount,
        BigDecimal holdingAmount,
        BigDecimal realizedPnl,
        BigDecimal unrealizedPnl,
        BigDecimal totalReturn,
        BigDecimal returnRate,
        boolean realizedComplete,
        List<FundReturnView> funds) {
}
