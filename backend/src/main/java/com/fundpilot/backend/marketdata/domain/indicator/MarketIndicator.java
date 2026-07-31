package com.fundpilot.backend.marketdata.domain.indicator;

import java.math.BigDecimal;
import java.time.Instant;

public record MarketIndicator(long fundProductId, String fundCode, Instant snapshotDate,
                              BigDecimal currentNav, Boolean priceAboveYearLine,
                              boolean yearLineRising, String weeklyMacdState,
                              String volumeState, BigDecimal weeklyDropPercent,
                              boolean sixtyDayHigh) {
    public MarketIndicator {
        if (fundProductId <= 0) throw new IllegalArgumentException("基金产品标识必须为正数");
        if (fundCode == null || fundCode.isBlank()) throw new IllegalArgumentException("基金代码不能为空");
        if (snapshotDate == null) throw new IllegalArgumentException("快照日期不能为空");
        fundCode = fundCode.trim();
    }
}
