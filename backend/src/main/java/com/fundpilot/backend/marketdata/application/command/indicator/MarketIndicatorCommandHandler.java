package com.fundpilot.backend.marketdata.application.command.indicator;

import com.fundpilot.backend.marketdata.domain.indicator.MarketIndicator;
import com.fundpilot.backend.marketdata.domain.indicator.MarketIndicatorRepository;
import com.fundpilot.backend.marketdata.domain.indicator.VolumeState;
import com.fundpilot.backend.marketdata.domain.indicator.WeeklyMacdState;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketIndicatorCommandHandler {
    private final MarketIndicatorRepository indicators;
    @Transactional public Result upsert(Long legacyFundId, long fundProductId, String fundCode,
                                        Instant snapshotDate, BigDecimal currentNav,
                                        Boolean priceAboveYearLine, boolean yearLineRising,
                                        WeeklyMacdState weeklyMacdState, VolumeState volumeState,
                                        BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {
        MarketIndicator saved = indicators.upsert(legacyFundId, new MarketIndicator(fundProductId,
                fundCode, snapshotDate, currentNav, priceAboveYearLine, yearLineRising,
                weeklyMacdState, volumeState, weeklyDropPercent, sixtyDayHigh));
        return Result.from(saved);
    }
    public record Result(long fundProductId, String fundCode, Instant snapshotDate,
                         BigDecimal currentNav, Boolean priceAboveYearLine,
                         boolean yearLineRising, String weeklyMacdState, String volumeState,
                         BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {
        static Result from(MarketIndicator i) { return new Result(i.fundProductId(), i.fundCode(), i.snapshotDate(), i.currentNav(), i.priceAboveYearLine(), i.yearLineRising(), name(i.weeklyMacdState()), name(i.volumeState()), i.weeklyDropPercent(), i.sixtyDayHigh()); }
        private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    }
}
