package com.fundpilot.backend.portfolio.controller;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PortfolioReturnTrendView(
        Instant dataStartDate,
        Instant latestDate,
        boolean dataSufficient,
        boolean valuationComplete,
        List<String> missingFundCodes,
        BigDecimal intervalReturn,
        BigDecimal intervalReturnRate,
        BigDecimal investedAmount,
        BigDecimal redeemedAmount,
        BigDecimal feeAmount,
        BigDecimal maximumReturn,
        BigDecimal maximumDrawdown,
        List<PortfolioReturnTrendPointView> points) {
}
