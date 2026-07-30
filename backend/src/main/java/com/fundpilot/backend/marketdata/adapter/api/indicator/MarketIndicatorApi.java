package com.fundpilot.backend.marketdata.adapter.api.indicator;

import com.fundpilot.backend.marketdata.application.command.indicator.MarketIndicatorCommandHandler;
import com.fundpilot.backend.marketdata.application.query.indicator.MarketIndicatorQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketIndicatorApi {
    private final MarketIndicatorCommandHandler commands;
    private final MarketIndicatorQueryHandler queries;
    public Snapshot upsert(Upsert request) { return from(commands.upsert(request.legacyFundId(), request.fundProductId(), request.fundCode(), request.snapshotDate(), request.currentNav(), request.priceAboveYearLine(), request.yearLineRising(), request.weeklyMacdState(), request.volumeState(), request.weeklyDropPercent(), request.sixtyDayHigh())); }
    public Optional<Snapshot> find(long productId, Instant date) { return queries.find(productId, date).map(MarketIndicatorApi::from); }
    private static Snapshot from(MarketIndicatorCommandHandler.Result i) { return new Snapshot(i.fundProductId(), i.fundCode(), i.snapshotDate(), i.currentNav(), i.priceAboveYearLine(), i.yearLineRising(), i.weeklyMacdState(), i.volumeState(), i.weeklyDropPercent(), i.sixtyDayHigh()); }
    private static Snapshot from(MarketIndicatorQueryHandler.Result i) { return new Snapshot(i.fundProductId(), i.fundCode(), i.snapshotDate(), i.currentNav(), i.priceAboveYearLine(), i.yearLineRising(), i.weeklyMacdState(), i.volumeState(), i.weeklyDropPercent(), i.sixtyDayHigh()); }
    public record Upsert(Long legacyFundId, long fundProductId, String fundCode, Instant snapshotDate, BigDecimal currentNav, boolean priceAboveYearLine, boolean yearLineRising, String weeklyMacdState, String volumeState, BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {}
    public record Snapshot(long fundProductId, String fundCode, Instant snapshotDate, BigDecimal currentNav, boolean priceAboveYearLine, boolean yearLineRising, String weeklyMacdState, String volumeState, BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {}
}
