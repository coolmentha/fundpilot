package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.query.realtimevaluation.RealtimeMarketOverviewQueryHandler.MarketVolumePriceAnalysis;
import java.math.BigDecimal;
import java.time.Instant;

/** 上证指数实时量价关系及行情阶段。 */
public record MarketVolumePriceView(
        String state,
        String phase,
        BigDecimal changePct,
        BigDecimal volumeRatio,
        Instant quoteTime) {

    public static MarketVolumePriceView from(MarketVolumePriceAnalysis analysis) {
        return new MarketVolumePriceView(analysis.state().name(), analysis.phase().name(),
                analysis.changePct(), analysis.volumeRatio(), analysis.quoteTime());
    }
}
