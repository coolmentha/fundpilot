package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indicator.MarketIndicatorCommandHandler;
import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.MarketIndicatorRefreshEventGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MarketIndicatorRefreshCommandHandlerDateTest {
    @Test
    void refreshOne_北京时间凌晨写入当天UTC日期标签() {
        var navSource = mock(PublishedNavSourceGateway.class);
        var indicators = mock(MarketIndicatorCommandHandler.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        MarketIndicatorRefreshCommandHandler handler = new MarketIndicatorRefreshCommandHandler(
                mock(TrackedNavProductGateway.class), navSource, mock(PublishedIndexKlineSourceGateway.class),
                mock(MarketIndicatorRefreshEventGateway.class), mock(NavPublishingCommandHandler.class),
                mock(IndexKlineCommandHandler.class), mock(IndexKlineQueryHandler.class), indicators, clock);
        when(navSource.fetchHistory("510300")).thenReturn(List.of(
                nav("2026-07-03T00:00:00Z", "1.00"), nav("2026-07-06T00:00:00Z", "1.01")));

        handler.refreshOne(new MarketIndicatorRefreshCommandHandler.RefreshTarget(1L, 11L, "510300",
                "测试基金", null, null));

        ArgumentCaptor<Instant> date = ArgumentCaptor.forClass(Instant.class);
        verify(indicators).upsert(eq(1L), eq(11L), eq("510300"), date.capture(), any(), anyBoolean(),
                anyBoolean(), any(), any(), any(), anyBoolean());
        assertThat(date.getValue()).isEqualTo(Instant.parse("2026-07-07T00:00:00Z"));
    }

    private static PublishedNavSourceGateway.NavSnapshot nav(String date, String value) {
        BigDecimal nav = new BigDecimal(value);
        return new PublishedNavSourceGateway.NavSnapshot(Instant.parse(date), nav, nav);
    }
}
