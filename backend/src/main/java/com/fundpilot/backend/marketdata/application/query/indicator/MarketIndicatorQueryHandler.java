package com.fundpilot.backend.marketdata.application.query.indicator;

import com.fundpilot.backend.marketdata.domain.indicator.MarketIndicator;
import com.fundpilot.backend.marketdata.domain.indicator.MarketIndicatorRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MarketIndicatorQueryHandler {
    private final MarketIndicatorRepository indicators;
    @Transactional(readOnly = true) public Optional<Result> find(long productId, Instant date) {
        return indicators.find(productId, date).map(Result::from);
    }
    public record Result(long fundProductId, String fundCode, Instant snapshotDate,
                         BigDecimal currentNav, boolean priceAboveYearLine,
                         boolean yearLineRising, String weeklyMacdState, String volumeState,
                         BigDecimal weeklyDropPercent, boolean sixtyDayHigh) {
        static Result from(MarketIndicator i) { return new Result(i.fundProductId(), i.fundCode(), i.snapshotDate(), i.currentNav(), i.priceAboveYearLine(), i.yearLineRising(), i.weeklyMacdState(), i.volumeState(), i.weeklyDropPercent(), i.sixtyDayHigh()); }
    }
}
