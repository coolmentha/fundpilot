package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeMarketOverviewQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketVolumePriceViewTest {

    @Test
    void from_映射稳定枚举和数值契约() {
        Instant quoteTime = Instant.parse("2026-07-10T05:30:00Z");
        var analysis = new RealtimeMarketOverviewQueryHandler.MarketVolumePriceAnalysis(
                RealtimeMarketOverviewQueryHandler.VolumePriceState.HIGH_UP,
                RealtimeMarketOverviewQueryHandler.MarketPhase.INTRADAY_ESTIMATE,
                new BigDecimal("0.0037"), new BigDecimal("1.68"), quoteTime);

        assertThat(MarketVolumePriceView.from(analysis)).isEqualTo(new MarketVolumePriceView(
                "HIGH_UP", "INTRADAY_ESTIMATE", new BigDecimal("0.0037"),
                new BigDecimal("1.68"), quoteTime));
    }
}
