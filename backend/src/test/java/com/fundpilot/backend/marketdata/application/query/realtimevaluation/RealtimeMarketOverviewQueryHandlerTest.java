package com.fundpilot.backend.marketdata.application.query.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeMarketOverviewGateway;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeMarketOverviewQueryHandlerTest {

    @Test
    void findStatus_按交易日历和北京时间区分市场状态() {
        assertState("2026-07-10T01:00:00Z", true, "PRE_OPEN");
        assertState("2026-07-10T01:30:00Z", true, "TRADING");
        assertState("2026-07-10T03:30:00Z", true, "LUNCH_BREAK");
        assertState("2026-07-10T05:00:00Z", true, "TRADING");
        assertState("2026-07-10T07:00:00Z", true, "CLOSED");
        assertState("2026-07-11T02:00:00Z", false, "NON_TRADING_DAY");
    }

    @Test
    void findStatus_透传最旧成功快照时间() {
        RealtimeMarketOverviewGateway cache = mock(RealtimeMarketOverviewGateway.class);
        TradingCalendarQueryHandler calendar = mock(TradingCalendarQueryHandler.class);
        Instant updatedAt = Instant.parse("2026-07-10T06:59:30Z");
        when(calendar.isTradingDay(any())).thenReturn(true);
        when(cache.findUpdatedAt()).thenReturn(updatedAt);

        var result = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(Instant.parse("2026-07-10T07:00:00Z"), ZoneOffset.UTC)).findStatus();

        assertThat(result.updatedAt()).isEqualTo(updatedAt);
    }

    private static void assertState(String now, boolean tradingDay, String expected) {
        RealtimeMarketOverviewGateway cache = mock(RealtimeMarketOverviewGateway.class);
        TradingCalendarQueryHandler calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(tradingDay);
        var handler = new RealtimeMarketOverviewQueryHandler(cache, calendar,
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC));

        assertThat(handler.findStatus().marketState().name()).isEqualTo(expected);
    }
}
