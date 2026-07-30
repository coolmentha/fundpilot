package com.fundpilot.backend.insights.domain.portfolioreturn;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioReturnSnapshot(Long id, long ownerId, Instant businessDate, BigDecimal investedAmount,
                                      BigDecimal redeemedAmount, BigDecimal feeAmount, BigDecimal holdingAmount,
                                      BigDecimal realizedPnl, BigDecimal unrealizedPnl, BigDecimal totalReturn,
                                      boolean valuationComplete, String missingFundCodes, Instant capturedAt) {
}
