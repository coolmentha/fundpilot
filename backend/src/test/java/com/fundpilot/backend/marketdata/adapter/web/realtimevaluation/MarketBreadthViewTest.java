package com.fundpilot.backend.marketdata.adapter.web.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway.Breadth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketBreadthViewTest {

    @Test
    void from_保留五项市场宽度数据() {
        assertThat(MarketBreadthView.from(new Breadth(3814, 1701, 153, 42, 25)))
                .isEqualTo(new MarketBreadthView(3814, 1701, 153, 42, 25));
    }
}
