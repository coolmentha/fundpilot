package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.FundIntradayChart;
import com.fundpilot.backend.market.service.KlineService;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketDataControllerTest {

    @Test
    void intraday_只投影实时缓存分钟线() {
        MarketRealtimeCache cache = mock(MarketRealtimeCache.class);
        FundIntradayChart chart = new FundIntradayChart("2026-07-24", "2026-07-23", new BigDecimal("1.0000"), List.of(
                new FundIntradayChart.Point("09:30", new BigDecimal("1.0010")),
                new FundIntradayChart.Point("09:31", new BigDecimal("1.0020"))));
        when(cache.getIntraday(1L)).thenReturn(chart);
        MarketDataController controller = new MarketDataController(
                mock(MarketIndicatorProvider.class), mock(KlineService.class), cache);

        FundIntradayView view = controller.intraday(1L).data();

        assertThat(view.estimateDate()).isEqualTo("2026-07-24");
        assertThat(view.points()).extracting(FundIntradayView.Point::time).containsExactly("09:30", "09:31");
    }
}
