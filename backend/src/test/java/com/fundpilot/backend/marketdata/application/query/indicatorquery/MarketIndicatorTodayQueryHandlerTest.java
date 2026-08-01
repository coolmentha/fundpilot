package com.fundpilot.backend.marketdata.application.query.indicatorquery;

import com.fundpilot.backend.marketdata.application.gateway.portfoliofund.OwnedFundProductGateway;
import com.fundpilot.backend.marketdata.application.query.indicator.MarketIndicatorQueryHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketIndicatorTodayQueryHandlerTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);

    @Test
    void find_北京时间凌晨读取当天UTC日期标签() {
        OwnedFundProductGateway products = mock(OwnedFundProductGateway.class);
        MarketIndicatorQueryHandler indicators = mock(MarketIndicatorQueryHandler.class);
        when(products.findOwned(1L)).thenReturn(Optional.of(new OwnedFundProductGateway.Product(11L, "510300",
                "000300", OwnedFundProductGateway.ProductType.ETF)));
        Instant today = Instant.parse("2026-07-07T00:00:00Z");
        when(indicators.find(11L, today)).thenReturn(Optional.of(new MarketIndicatorQueryHandler.Result(11L,
                "510300", today, new BigDecimal("1.01"), true, true, "NORMAL", "NORMAL",
                BigDecimal.ZERO, false)));
        MarketIndicatorTodayQueryHandler handler = new MarketIndicatorTodayQueryHandler(products, indicators, CLOCK);

        Optional<MarketIndicatorTodayQueryHandler.Snapshot> snapshot = handler.find(1L);

        assertThat(snapshot).isPresent();
        verify(indicators).find(11L, today);
    }
}
