package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RealtimeMarketOverviewQueryHandler {
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);
    private static final BigDecimal HIGH_VOLUME_RATIO = new BigDecimal("1.5");
    private static final BigDecimal LOW_VOLUME_RATIO = new BigDecimal("0.5");
    private static final Duration INTRADAY_STALE_AFTER = Duration.ofMinutes(2);

    private final RealtimeMarketOverviewGateway cache;
    private final TradingCalendarQueryHandler calendar;
    private final Clock clock;

    public List<RealtimeMarketOverviewGateway.IndexQuote> findCurrentActorIndices() {
        return cache.findCurrentActorIndices();
    }

    public RealtimeMarketOverviewGateway.Breadth findBreadth() {
        return cache.findBreadth();
    }

    public MarketVolumePriceAnalysis findVolumePrice() {
        Instant now = clock.instant();
        MarketState marketState = marketState(now);
        MarketPhase phase = marketState == MarketState.TRADING || marketState == MarketState.LUNCH_BREAK
                ? MarketPhase.INTRADAY_ESTIMATE : MarketPhase.CLOSED;
        RealtimeMarketOverviewGateway.MarketVolumePrice snapshot = cache.findMarketVolumePrice();
        if (!isCurrent(snapshot, marketState, now)) {
            return new MarketVolumePriceAnalysis(VolumePriceState.UNAVAILABLE, phase,
                    null, null, snapshot == null ? null : snapshot.quoteTime());
        }
        return new MarketVolumePriceAnalysis(classify(snapshot.changePct(), snapshot.volumeRatio()), phase,
                snapshot.changePct(), snapshot.volumeRatio(), snapshot.quoteTime());
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
        return new MarketStatus(marketState(now), cache.findUpdatedAt());
    }

    private MarketState marketState(Instant now) {
        if (!calendar.isTradingDay(ChinaTradingDate.toUtcDate(now))) {
            return MarketState.NON_TRADING_DAY;
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
        return state;
    }

    private boolean isCurrent(RealtimeMarketOverviewGateway.MarketVolumePrice snapshot,
                              MarketState marketState, Instant now) {
        if (snapshot == null || snapshot.changePct() == null || snapshot.volumeRatio() == null
                || snapshot.volumeRatio().signum() <= 0 || snapshot.quoteTime() == null) {
            return false;
        }
        if (marketState == MarketState.TRADING
                && snapshot.quoteTime().isBefore(now.minus(INTRADAY_STALE_AFTER))) {
            return false;
        }
        Instant today = ChinaTradingDate.toUtcDate(now);
        Optional<Instant> expectedDate = switch (marketState) {
            case PRE_OPEN -> calendar.latestBefore(today);
            case NON_TRADING_DAY -> calendar.latestOnOrBefore(today);
            case TRADING, LUNCH_BREAK, CLOSED -> Optional.of(today);
        };
        return expectedDate.isPresent()
                && ChinaTradingDate.toUtcDate(snapshot.quoteTime()).equals(expectedDate.get());
    }

    private static VolumePriceState classify(BigDecimal changePct, BigDecimal volumeRatio) {
        if (changePct.signum() == 0) {
            return VolumePriceState.FLAT;
        }
        boolean up = changePct.signum() > 0;
        if (volumeRatio.compareTo(HIGH_VOLUME_RATIO) >= 0) {
            return up ? VolumePriceState.HIGH_UP : VolumePriceState.HIGH_DOWN;
        }
        if (volumeRatio.compareTo(LOW_VOLUME_RATIO) <= 0) {
            return up ? VolumePriceState.LOW_UP : VolumePriceState.LOW_DOWN;
        }
        return up ? VolumePriceState.NORMAL_UP : VolumePriceState.NORMAL_DOWN;
    }

    public enum MarketState { PRE_OPEN, TRADING, LUNCH_BREAK, CLOSED, NON_TRADING_DAY }
    public enum MarketPhase { INTRADAY_ESTIMATE, CLOSED }
    public enum VolumePriceState {
        HIGH_UP, LOW_UP, HIGH_DOWN, LOW_DOWN, NORMAL_UP, NORMAL_DOWN, FLAT, UNAVAILABLE
    }
    public record MarketStatus(MarketState marketState, Instant updatedAt) {}
    public record MarketVolumePriceAnalysis(VolumePriceState state, MarketPhase phase,
                                            BigDecimal changePct, BigDecimal volumeRatio,
                                            Instant quoteTime) {}
}
