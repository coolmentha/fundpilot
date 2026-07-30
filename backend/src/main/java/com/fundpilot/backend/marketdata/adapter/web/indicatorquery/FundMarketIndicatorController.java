package com.fundpilot.backend.marketdata.adapter.web.indicatorquery;

import com.fundpilot.backend.marketdata.application.query.indicatorquery.MarketIndicatorTodayQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FundMarketIndicatorController {
    private final MarketIndicatorTodayQueryHandler queries;

    @GetMapping("/api/funds/{fundId}/market-indicators/today")
    public ApiResponse<MarketIndicatorSnapshotView> today(@PathVariable long fundId) {
        return ApiResponse.ok(queries.find(fundId).map(value -> MarketIndicatorSnapshotView.from(fundId, value))
                .orElse(null));
    }

    @GetMapping("/api/portfolio-funds/{portfolioFundId}/market-indicators/today")
    public ApiResponse<MarketIndicatorSnapshotView> portfolioFundToday(@PathVariable long portfolioFundId) {
        return ApiResponse.ok(queries.findForPortfolioFund(portfolioFundId)
                .map(value -> MarketIndicatorSnapshotView.from(portfolioFundId, value)).orElse(null));
    }

    public record MarketIndicatorSnapshotView(Long fundId, String fundCode, Instant snapshotDate,
                                              BigDecimal currentNav, boolean priceAboveYearLine,
                                              boolean yearLineRising, String weeklyMacdState, String volumeState,
                                              BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {
        static MarketIndicatorSnapshotView from(long fundId, MarketIndicatorTodayQueryHandler.Snapshot value) {
            return new MarketIndicatorSnapshotView(fundId, value.fundCode(), value.snapshotDate(),
                    value.currentNav(), value.priceAboveYearLine(), value.yearLineRising(),
                    value.weeklyMacdState(), value.volumeState(), value.weeklyDropPercent(), value.sixtyDayHigh());
        }
    }
}
