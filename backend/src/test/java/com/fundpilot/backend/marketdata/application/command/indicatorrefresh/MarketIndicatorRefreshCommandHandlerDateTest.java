package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indicator.MarketIndicatorCommandHandler;
import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.MarketIndicatorRefreshEventGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
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
                mock(IndexKlineCommandHandler.class), mock(IndexKlineQueryHandler.class), indicators, clock,
                mock(com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway.class),
                mock(com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler.class),
                mock(com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler.class));
        when(navSource.fetchHistory("510300")).thenReturn(List.of(
                nav("2026-07-03T00:00:00Z", "1.00"), nav("2026-07-06T00:00:00Z", "1.01")));

        handler.refreshOne(new MarketIndicatorRefreshCommandHandler.RefreshTarget(1L, 11L, "510300",
                "测试基金", null, null));

        ArgumentCaptor<Instant> date = ArgumentCaptor.forClass(Instant.class);
        verify(indicators).upsert(eq(1L), eq(11L), eq("510300"), date.capture(), any(), any(),
                anyBoolean(), any(), any(), any(), anyBoolean());
        assertThat(date.getValue()).isEqualTo(Instant.parse("2026-07-07T00:00:00Z"));
    }

    @Test
    void refreshOne_增量刷新基于已落库完整K线序列计算成交量状态() {
        var navSource = mock(PublishedNavSourceGateway.class);
        var klineSource = mock(PublishedIndexKlineSourceGateway.class);
        var klineQueries = mock(IndexKlineQueryHandler.class);
        var indicators = mock(MarketIndicatorCommandHandler.class);
        var klineCommands = mock(IndexKlineCommandHandler.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        MarketIndicatorRefreshCommandHandler handler = new MarketIndicatorRefreshCommandHandler(
                mock(TrackedNavProductGateway.class), navSource, klineSource,
                mock(MarketIndicatorRefreshEventGateway.class), mock(NavPublishingCommandHandler.class),
                klineCommands, klineQueries, indicators, clock,
                mock(com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway.class),
                mock(com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler.class),
                mock(com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler.class));
        when(navSource.fetchHistory("510300")).thenReturn(List.of(
                nav("2026-07-03T00:00:00Z", "1.00"), nav("2026-07-06T00:00:00Z", "1.01")));
        when(klineQueries.exists("000300")).thenReturn(true);
        when(klineSource.fetch(any(), any())).thenReturn(new PublishedIndexKlineSourceGateway.IndexKline(
                List.of(bar("2026-07-03T00:00:00Z"), bar("2026-07-06T00:00:00Z"))));
        List<IndexKlineQueryHandler.Bar> stored = new java.util.ArrayList<>();
        for (int index = 0; index < 25; index++) {
            Instant date = Instant.parse("2026-06-01T00:00:00Z").plus(java.time.Duration.ofDays(index));
            stored.add(new IndexKlineQueryHandler.Bar(date, new BigDecimal("1"), new BigDecimal("2"),
                    new BigDecimal("1"), new BigDecimal("2"), 100L));
        }
        when(klineQueries.findAll("000300")).thenReturn(stored);

        handler.refreshOne(new MarketIndicatorRefreshCommandHandler.RefreshTarget(1L, 11L, "510300",
                "测试基金", "000300", null));

        ArgumentCaptor<String> volumeState = ArgumentCaptor.forClass(String.class);
        verify(indicators).upsert(eq(1L), eq(11L), eq("510300"), any(), any(), any(), anyBoolean(),
                any(), volumeState.capture(), any(), anyBoolean());
        assertThat(volumeState.getValue()).isEqualTo("NORMAL");
    }

    @Test
    void refreshOne_估值已覆盖截至日时不重复请求远程历史() {
        var navSource = mock(PublishedNavSourceGateway.class);
        var valuationSource = mock(PublishedIndexValuationSourceGateway.class);
        var valuationCommands = mock(com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler.class);
        var valuationQueries = mock(IndexValuationQueryHandler.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        MarketIndicatorRefreshCommandHandler handler = new MarketIndicatorRefreshCommandHandler(
                mock(TrackedNavProductGateway.class), navSource, mock(PublishedIndexKlineSourceGateway.class),
                mock(MarketIndicatorRefreshEventGateway.class), mock(NavPublishingCommandHandler.class),
                mock(IndexKlineCommandHandler.class), mock(IndexKlineQueryHandler.class),
                mock(MarketIndicatorCommandHandler.class), clock, valuationSource, valuationCommands, valuationQueries);
        when(navSource.fetchHistory("510300")).thenReturn(List.of(
                nav("2026-07-03T00:00:00Z", "1.00"), nav("2026-07-06T00:00:00Z", "1.01")));
        when(valuationQueries.latest("000300", "CSINDEX_INDEX_CSI_DS_PE_PEG"))
                .thenReturn(Optional.of(new IndexValuationQueryHandler.Valuation("000300",
                        Instant.parse("2026-07-06T00:00:00Z"), new BigDecimal("12.45"),
                        "CSINDEX_INDEX_CSI_DS_PE_PEG")));

        handler.refreshOne(new MarketIndicatorRefreshCommandHandler.RefreshTarget(1L, 11L, "510300",
                "测试基金", "000300", null));

        verifyNoInteractions(valuationSource, valuationCommands);
    }

    private static PublishedNavSourceGateway.NavSnapshot nav(String date, String value) {
        BigDecimal nav = new BigDecimal(value);
        return new PublishedNavSourceGateway.NavSnapshot(Instant.parse(date), nav, nav);
    }

    private static PublishedIndexKlineSourceGateway.Bar bar(String date) {
        return new PublishedIndexKlineSourceGateway.Bar(Instant.parse(date), new BigDecimal("1"),
                new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("2"), 100L);
    }
}
