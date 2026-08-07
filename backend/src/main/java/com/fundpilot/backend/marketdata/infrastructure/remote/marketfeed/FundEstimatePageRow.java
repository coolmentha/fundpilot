package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import java.math.BigDecimal;

/** 静态估值页中一只基金的估算涨跌率。 */
public record FundEstimatePageRow(
        BigDecimal estimatedChangePct,
        String estimateDate,
        String baseNavDate) {
}
