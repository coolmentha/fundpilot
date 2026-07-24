package com.fundpilot.backend.market.controller;

import com.fundpilot.backend.market.client.MarketBreadthSnapshot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarketBreadthViewTest {

    @Test
    void from_保留四项市场宽度数据() {
        assertThat(MarketBreadthView.from(new MarketBreadthSnapshot(3814, 1701, 42, 25)))
                .isEqualTo(new MarketBreadthView(3814, 1701, 42, 25));
    }
}
