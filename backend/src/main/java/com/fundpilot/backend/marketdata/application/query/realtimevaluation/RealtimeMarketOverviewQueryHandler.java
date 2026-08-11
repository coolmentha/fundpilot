package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeMarketOverviewQueryHandler {
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private final RealtimeMarketOverviewGateway cache;
    private final TradingCalendarQueryHandler calendar;
    private final Clock clock;

    public List<RealtimeMarketOverviewGateway.IndexQuote> findCurrentActorIndices() {
        return cache.findCurrentActorIndices();
    }

    public RealtimeMarketOverviewGateway.Breadth findBreadth() {
        return cache.findBreadth();
    }

    public Map<String, RealtimeMarketOverviewGateway.Estimate> findEstimates(List<String> fundCodes) {
        return cache.findEstimates(fundCodes);
    }

    public List<RealtimeMarketOverviewGateway.Sector> findSectors() {
        return cache.findSectors();
    }

    public RealtimeMarketOverviewGateway.MoneyFlow findMoneyFlow() {
        return cache.findMoneyFlow();
    }

    public MarketStatus findStatus() {
        Instant now = clock.instant();
        if (!calendar.isTradingDay(ChinaTradingDate.toUtcDate(now))) {
            return new MarketStatus(MarketState.NON_TRADING_DAY, cache.findUpdatedAt());
        }
        LocalTime time = now.atZone(ChinaTradingDate.ZONE).toLocalTime();
        MarketState state;
        if (time.isBefore(MORNING_OPEN)) {
            state = MarketState.PRE_OPEN;
        } else if (time.isBefore(MORNING_CLOSE)) {
            state = MarketState.TRADING;
        } else if (time.isBefore(AFTERNOON_OPEN)) {
            state = MarketState.LUNCH_BREAK;
        } else if (time.isBefore(AFTERNOON_CLOSE)) {
            state = MarketState.TRADING;
        } else {
            state = MarketState.CLOSED;
        }
        return new MarketStatus(state, cache.findUpdatedAt());
    }

    public enum MarketState { PRE_OPEN, TRADING, LUNCH_BREAK, CLOSED, NON_TRADING_DAY }
    public record MarketStatus(MarketState marketState, Instant updatedAt) {}
}
