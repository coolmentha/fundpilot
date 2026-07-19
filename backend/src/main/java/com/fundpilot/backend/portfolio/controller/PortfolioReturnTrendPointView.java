package com.fundpilot.backend.portfolio.controller;

import com.fundpilot.backend.portfolio.entity.PortfolioReturnSnapshotEntity;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioReturnTrendPointView(
        Instant date, BigDecimal totalReturn, BigDecimal holdingAmount,
        BigDecimal investedAmount, BigDecimal redeemedAmount, boolean valuationComplete) {
    public static PortfolioReturnTrendPointView from(PortfolioReturnSnapshotEntity row) {
        return new PortfolioReturnTrendPointView(row.getBusinessDate(), row.getTotalReturn(), row.getHoldingAmount(),
                row.getInvestedAmount(), row.getRedeemedAmount(), row.isValuationComplete());
    }
}
