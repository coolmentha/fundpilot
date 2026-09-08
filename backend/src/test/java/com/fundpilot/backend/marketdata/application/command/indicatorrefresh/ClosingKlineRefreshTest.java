package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClosingKlineRefreshTest {
    private static final Instant DATE = Instant.parse("2026-09-08T00:00:00Z");
    private static final Instant CUTOFF = Instant.parse("2026-09-08T09:00:00Z");
    private static final String CODE = "931079.CSI";
    @Mock TrackedNavProductGateway products;
    @Mock PublishedNavSourceGateway navSource;
    @Mock PublishedIndexKlineSourceGateway source;
    @Mock MarketIndicatorRefreshEventGateway events;
    @Mock NavPublishingCommandHandler navPublisher;
    @Mock IndexKlineCommandHandler writer;
    @Mock IndexKlineQueryHandler queries;
    @Mock MarketIndicatorCommandHandler indicators;
    @Mock Clock clock;
    @Mock PublishedIndexValuationSourceGateway valuationSource;
    @Mock IndexValuationCommandHandler valuations;
    @Mock IndexValuationQueryHandler valuationQueries;
    @InjectMocks MarketIndicatorRefreshCommandHandler handler;

    @BeforeEach
    void clockAtFirstAttempt() {
        when(clock.instant()).thenReturn(CUTOFF);
    }

    @Test
    void 未到17点不拉取() {
        when(clock.instant()).thenReturn(CUTOFF.minusSeconds(1));
        handler.refreshClosingKlines(DATE);
        verifyNoInteractions(products, queries, source, writer);
    }

    @Test
    void 缺目标日留待下一轮成功后跳过且相同指数去重() {
        when(products.findAll()).thenReturn(List.of(product(CODE), product(CODE), product(null), product(" ")));
        when(queries.completeCodesForDate(DATE, CUTOFF)).thenReturn(Set.of(), Set.of(), Set.of(CODE));
        when(queries.existingCodes()).thenReturn(Set.of(CODE));
        var today = bar(DATE);
        when(source.fetch("2.931079", "10")).thenReturn(
                result(bar(DATE.minus(1, ChronoUnit.DAYS))), result(today));

        handler.refreshClosingKlines(DATE);
        verifyNoInteractions(writer);
        handler.refreshClosingKlines(DATE);
        handler.refreshClosingKlines(DATE);

        verify(source, times(2)).fetch("2.931079", "10");
        verify(queries, times(3)).existingCodes();
        verify(queries, never()).exists(any());
        verify(writer).upsert(CODE, List.of(new IndexKlineCommandHandler.Bar(DATE,
                today.open(), today.high(), today.low(), today.close(), today.volume())));
        verifyNoMoreInteractions(writer);
        verifyNoInteractions(navSource, navPublisher, events, indicators, valuationSource, valuations, valuationQueries);
    }

    @Test
    void 单个指数失败不阻断其他指数首次拉完整窗口() {
        when(products.findAll()).thenReturn(List.of(product(CODE), product("000300.SH")));
        when(source.fetch("2.931079", "400")).thenThrow(new IllegalStateException("test source unavailable"));
        when(source.fetch("1.000300", "400")).thenReturn(result(bar(DATE)));

        handler.refreshClosingKlines(DATE);

        verify(writer).upsert(eq("000300.SH"), any());
        verify(writer, never()).upsert(eq(CODE), any());
    }

    @Test
    void 空响应残缺目标日及未来日期均不标记成功() {
        when(products.findAll()).thenReturn(List.of(product(CODE)));
        var missingOpen = new PublishedIndexKlineSourceGateway.Bar(DATE, null, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, 1);
        var impossibleHigh = new PublishedIndexKlineSourceGateway.Bar(DATE, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.TEN, 1);
        var negativeVolume = new PublishedIndexKlineSourceGateway.Bar(DATE, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, -1);
        when(source.fetch("2.931079", "400")).thenReturn(null,
                new PublishedIndexKlineSourceGateway.IndexKline(null), result(missingOpen),
                result(impossibleHigh), result(negativeVolume), result(bar(DATE.plus(1, ChronoUnit.DAYS))));

        for (int attempt = 0; attempt < 6; attempt++) handler.refreshClosingKlines(DATE);

        verify(source, times(6)).fetch("2.931079", "400");
        verifyNoInteractions(writer);
    }

    private static TrackedNavProductGateway.TrackedProduct product(String code) {
        return new TrackedNavProductGateway.TrackedProduct(1L, 1L, "test", "test", code, null);
    }

    private static PublishedIndexKlineSourceGateway.Bar bar(Instant date) {
        return new PublishedIndexKlineSourceGateway.Bar(date, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.ONE, BigDecimal.TEN, 0);
    }

    private static PublishedIndexKlineSourceGateway.IndexKline result(PublishedIndexKlineSourceGateway.Bar bar) {
        return new PublishedIndexKlineSourceGateway.IndexKline(List.of(bar));
    }
}
