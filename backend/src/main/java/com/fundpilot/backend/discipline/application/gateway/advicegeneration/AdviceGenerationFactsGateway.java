package com.fundpilot.backend.discipline.application.gateway.advicegeneration;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** 每日纪律建议计算所需的跨模块事实快照。 */
public interface AdviceGenerationFactsGateway {
    boolean isTradingDay(Instant businessDate);
    long tradingDaysBetween(Instant fromExclusive, Instant toInclusive);
    Optional<Facts> load(long ownerId, long portfolioFundId, Instant businessDate);

    record Facts(long portfolioFundId, long ownerId, long fundProductId, String productType,
                 String positionStatus, Instant openedAt, BigDecimal costPerShare,
                 BigDecimal holdingShares, MarketSnapshot market, BigDecimal currentUnitNav,
                 BigDecimal currentAccumulatedNav, BigDecimal peakAccumulatedNav,
                 BigDecimal holdingPeriodPeakNav, Instant lastBuyTime,
                 BigDecimal matureRedeemableShares) {
    }

    record MarketSnapshot(BigDecimal currentNav, Boolean priceAboveYearLine,
                          boolean yearLineRising, String weeklyMacdState, String volumeState,
                          BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {
    }
}
