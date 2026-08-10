package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyPush2Client;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundIntradayChart;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.IndexRealtimeSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MarketBreadthSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsIndexFlashClient;
import com.fundpilot.backend.marketdata.adapter.api.watchedindex.WatchedIndicesApi;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;

class MarketRealtimeCacheTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T05:30:00Z"), ZoneOffset.UTC);
    private static final String INDEX_FLASH = """
            {"zdt_data":{"zd_time":["14:59","15:00"],"ztzs":[25,42],"dtzs":[10,25]}}
            """;

    @Test
    void restoreFromRedis_启动时恢复持久化行情快照() {
        MarketRealtimeRedisStore redisStore = mock(MarketRealtimeRedisStore.class);
        IndexRealtimeSnapshot index = new IndexRealtimeSnapshot("1.000001", "上证指数",
                new BigDecimal("3500"), BigDecimal.TEN, new BigDecimal("0.003"), new BigDecimal("1000"));
        FundEstimateSnapshot estimate = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(redisStore.load()).thenReturn(Optional.of(new MarketRealtimeRedisStore.Snapshot(
                List.of(index), new MarketBreadthSnapshot(3000, 2000, 42, 25), List.of(), null,
                Map.of("510300", estimate), Map.of("510300", EstimateStatus.AVAILABLE))));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                mock(EastmoneyPush2Client.class), mock(FundEstimateService.class), mock(WatchedIndicesApi.class),
                mock(TrackedNavProductGateway.class), mock(MarketDataMetrics.class), CLOCK, redisStore, mock(ThsIndexFlashClient.class));

        cache.restoreFromRedis();

        assertThat(cache.getIndices()).containsExactly(index);
        assertThat(cache.getEstimates(List.of("510300"))).containsEntry("510300", estimate);
    }

    @Test
    void refreshRealtimeWithoutEstimates_一次请求同时刷新自选指数和市场宽度() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of("1.000300"));
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {"data":{"diff":[
                  {"f2":400000,"f3":20,"f4":800,"f6":1000,"f12":"000300","f14":"沪深300"},
                  {"f2":350000,"f3":10,"f4":300,"f6":2000,"f12":"000001","f14":"上证指数","f104":1542,"f105":763},
                  {"f2":120000,"f3":30,"f4":400,"f6":3000,"f12":"399001","f14":"深证成指","f104":2012,"f105":872},
                  {"f2":150000,"f3":40,"f4":500,"f6":4000,"f12":"899050","f14":"北证50","f104":260,"f105":66}
                ]}}
                """);
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH);
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();

        verify(push2Client).fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.argThat(secids ->
                secids.contains("1.000300")
                        && secids.contains("1.000001")
                        && secids.contains("0.399001")
                        && secids.contains("0.899050")));
        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000300");
        assertThat(cache.getBreadth()).isEqualTo(new MarketBreadthSnapshot(3814, 1701, 42, 25));
    }

    @Test
    void refreshRealtimeWithoutEstimates_残缺市场数据保留旧宽度缓存() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"data":{"diff":[
                          {"f12":"000001","f104":100,"f105":50},
                          {"f12":"399001","f104":200,"f105":80},
                          {"f12":"899050","f104":30,"f105":10}
                        ]}}
                        """)
                .thenReturn("""
                        {"data":{"diff":[
                          {"f12":"000001","f104":1,"f105":2},
                          {"f12":"399001","f104":3,"f105":4}
                        ]}}
                        """);
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH);
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();
        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getBreadth()).isEqualTo(new MarketBreadthSnapshot(330, 140, 42, 25));
    }

    @Test
    void refreshRealtimeWithoutEstimates_同花顺失败保留旧完整宽度缓存() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        String raw = """
                {"data":{"diff":[
                  {"f12":"000001","f104":100,"f105":50},
                  {"f12":"399001","f104":200,"f105":80},
                  {"f12":"899050","f104":30,"f105":10}
                ]}}
                """;
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString())).thenReturn(raw);
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH).thenThrow(new IllegalStateException("403"));
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, mock(FundEstimateService.class), userConfigService, mock(TrackedNavProductGateway.class),
                mock(MarketDataMetrics.class), CLOCK, mock(MarketRealtimeRedisStore.class), indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();
        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getBreadth()).isEqualTo(new MarketBreadthSnapshot(330, 140, 42, 25));
    }

    @Test
    void refreshAll_基金估值覆盖持仓和观察池基金() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());

        TrackedNavProductGateway.TrackedProduct holding = fund("510300", FundStatus.HOLDING);
        TrackedNavProductGateway.TrackedProduct watching = fund("159825", FundStatus.PENDING_HOLDING);
        when(products.findAll()).thenReturn(List.of(holding, watching));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.unavailable());
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("159825"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshAll();

        verify(products).findAll();
        verify(estimateService).fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet());
        verify(estimateService).fetchEstimateResult(org.mockito.ArgumentMatchers.eq("159825"), any(Instant.class), anySet());
    }

    @Test
    void refreshFundEstimates_只刷新基金估值() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("270042", FundStatus.HOLDING);
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("270042"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshFundEstimates();

        verify(estimateService).fetchEstimateResult(org.mockito.ArgumentMatchers.eq("270042"), any(Instant.class), anySet());
        verify(push2Client, never()).fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString());
        verify(push2Client, never()).fetchSectorListRaw(org.mockito.ArgumentMatchers.anyString());
        verify(push2Client, never()).fetchNorthboundRaw();
    }

    @Test
    void refreshFundEstimates_QDII不请求估值且清除旧缓存() {
        FundEstimateService estimateService = mock(FundEstimateService.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        MarketRealtimeRedisStore redisStore = mock(MarketRealtimeRedisStore.class);
        var domestic = fund("510300", TrackedNavProductGateway.InvestmentTarget.STOCK);
        var qdii = fund("968012", TrackedNavProductGateway.InvestmentTarget.QDII);
        FundEstimateSnapshot oldEstimate = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        FundIntradayChart oldChart = new FundIntradayChart(
                "2026-07-10", "2026-07-09", BigDecimal.ONE, List.of(
                        new FundIntradayChart.Point("13:29", new BigDecimal("1.0110")),
                        new FundIntradayChart.Point("13:30", new BigDecimal("1.0123"))));
        when(products.findAll()).thenReturn(List.of(domestic, qdii));
        when(redisStore.load()).thenReturn(java.util.Optional.of(new MarketRealtimeRedisStore.Snapshot(
                List.of(), null, List.of(), null,
                Map.of("968012", oldEstimate), Map.of("968012", EstimateStatus.AVAILABLE),
                Map.of("968012", oldChart))));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                mock(EastmoneyPush2Client.class), estimateService, mock(WatchedIndicesApi.class), products,
                mock(MarketDataMetrics.class), CLOCK, redisStore,
                mock(ThsIndexFlashClient.class));
        cache.restoreFromRedis();

        cache.refreshFundEstimates();

        verify(estimateService).fetchEstimateResult(
                org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet());
        verify(estimateService, never()).fetchEstimateResult(
                org.mockito.ArgumentMatchers.eq("968012"), any(Instant.class), anySet());
        assertThat(cache.getEstimates(List.of("968012"))).isEmpty();
        assertThat(cache.getEstimateStatus("968012")).isEqualTo(EstimateStatus.UNAVAILABLE);
        assertThat(cache.getIntraday("968012")).isNull();
    }

    @Test
    void refreshFundEstimates_达到总期限后下一轮从断点继续() {
        FundEstimateService estimateService = mock(FundEstimateService.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T05:30:00Z"));
        when(products.findAll()).thenReturn(List.of(
                fund("000001", TrackedNavProductGateway.InvestmentTarget.STOCK),
                fund("000002", TrackedNavProductGateway.InvestmentTarget.STOCK),
                fund("000003", TrackedNavProductGateway.InvestmentTarget.STOCK)));
        when(estimateService.fetchEstimateResult(anyString(), any(Instant.class), anySet())).thenAnswer(invocation -> {
            clock.advance(Duration.ofSeconds(13));
            return FundEstimateResult.unavailable();
        });
        MarketRealtimeCache cache = new MarketRealtimeCache(
                mock(EastmoneyPush2Client.class), estimateService, mock(WatchedIndicesApi.class), products,
                mock(MarketDataMetrics.class), clock, mock(MarketRealtimeRedisStore.class),
                mock(ThsIndexFlashClient.class));

        cache.refreshFundEstimates();
        verify(estimateService, never()).fetchEstimateResult(
                org.mockito.ArgumentMatchers.eq("000003"), any(Instant.class), anySet());

        cache.refreshFundEstimates();
        verify(estimateService).fetchEstimateResult(
                org.mockito.ArgumentMatchers.eq("000003"), any(Instant.class), anySet());
    }

    @Test
    void refreshFundEstimates_失败基金在冷却期内不重复请求且到期后恢复() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("270042", FundStatus.HOLDING);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-10T05:30:00Z"));
        FundEstimateSnapshot recovered = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("270042"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.failed(EstimateStatus.PARSE_ERROR))
                .thenReturn(FundEstimateResult.available(recovered));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), clock,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshFundEstimates();
        cache.refreshFundEstimates();

        verify(estimateService, times(1)).fetchEstimateResult(
                org.mockito.ArgumentMatchers.eq("270042"), any(Instant.class), anySet());
        assertThat(cache.getEstimateStatus("270042")).isEqualTo(EstimateStatus.PARSE_ERROR);

        clock.advance(Duration.ofMinutes(5));
        cache.refreshFundEstimates();

        verify(estimateService, times(2)).fetchEstimateResult(
                org.mockito.ArgumentMatchers.eq("270042"), any(Instant.class), anySet());
        assertThat(cache.getEstimates(List.of("270042"))).containsEntry("270042", recovered);
        assertThat(cache.getEstimateStatus("270042")).isEqualTo(EstimateStatus.AVAILABLE);
    }

    @Test
    void onApplicationReady_不在启动线程逐只刷新基金估值() throws Exception {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        assertThat(MarketRealtimeCache.class.getDeclaredMethod("onApplicationReady")
                .isAnnotationPresent(Async.class)).isTrue();
        cache.onApplicationReady();

        verify(products, never()).findAll();
        verify(estimateService, never()).fetchEstimateResult(anyString(), any(Instant.class), anySet());
    }

    @Test
    void warmFundEstimatesAfterReady_异步预热全部基金估值() throws Exception {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 15:00", "2026-07-09");
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.available(snapshot));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        var method = MarketRealtimeCache.class.getMethod("warmFundEstimatesAfterReady");

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        assertThat(method.isAnnotationPresent(EventListener.class)).isTrue();
        method.invoke(cache);
        assertThat(cache.getEstimates(List.of("510300"))).containsEntry("510300", snapshot);
    }

    @Test
    void refreshAll_成功后空响应会删除旧估值并标记不可用() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.available(snapshot))
                .thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshAll();
        cache.refreshAll();

        assertThat(cache.getEstimates(List.of("510300"))).isEmpty();
        assertThat(cache.hasEstimateFetchFailed("510300")).isFalse();
        assertThat(cache.getEstimateStatus("510300")).isEqualTo(EstimateStatus.UNAVAILABLE);
    }

    @Test
    void refreshFundEstimates_当天至少两点的同花顺分钟线才缓存() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot estimate = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        FundIntradayChart chart = new FundIntradayChart("2026-07-10", "2026-07-09", new BigDecimal("1.0000"), List.of(
                new FundIntradayChart.Point("13:29", new BigDecimal("1.0110")),
                new FundIntradayChart.Point("13:30", new BigDecimal("1.0123"))));
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.available(estimate, chart))
                .thenReturn(FundEstimateResult.available(estimate));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshFundEstimates();
        assertThat(cache.getIntraday("510300")).isEqualTo(chart);

        cache.refreshFundEstimates();
        assertThat(cache.getIntraday("510300")).isNull();
    }

    @Test
    void refreshAll_成功后异常会删除旧估值并标记失败() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.available(snapshot))
                .thenThrow(new IllegalStateException("timeout"));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshAll();
        cache.refreshAll();

        assertThat(cache.getEstimates(List.of("510300"))).isEmpty();
        assertThat(cache.hasEstimateFetchFailed("510300")).isTrue();
    }

    @Test
    void refreshAll_旧日期估值不进入缓存且后续当天估值可恢复() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot stale = new FundEstimateSnapshot(
                new BigDecimal("0.0100"), "2026-07-09 15:00", "2026-07-08");
        FundEstimateSnapshot current = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        when(products.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"), any(Instant.class), anySet()))
                .thenReturn(FundEstimateResult.available(stale))
                .thenReturn(FundEstimateResult.available(current));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshAll();

        assertThat(cache.getEstimates(List.of("510300"))).isEmpty();
        assertThat(cache.hasEstimateFetchFailed("510300")).isFalse();
        assertThat(cache.getEstimateStatus("510300")).isEqualTo(EstimateStatus.STALE);

        cache.refreshAll();

        assertThat(cache.getEstimates(List.of("510300"))).containsEntry("510300", current);
        assertThat(cache.hasEstimateFetchFailed("510300")).isFalse();
    }

    @Test
    void refreshAll_货币基金直接标记不可用且不调用普通估值源() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        TrackedNavProductGateway.TrackedProduct fund = new TrackedNavProductGateway.TrackedProduct(null, 1L,
                "000009", "易方达天天理财货币A", null,
                TrackedNavProductGateway.InvestmentTarget.MONEY_MARKET);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        when(products.findAll()).thenReturn(List.of(fund));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), mock(ThsIndexFlashClient.class));

        cache.refreshAll();

        verify(estimateService, never()).fetchEstimateResult(anyString(), any(Instant.class), anySet());
        assertThat(cache.getEstimateStatus("000009")).isEqualTo(EstimateStatus.UNAVAILABLE);
        assertThat(cache.hasEstimateFetchFailed("000009")).isFalse();
    }

    @Test
    void refreshRealtimeWithoutEstimates_上游空diff_保留旧指数缓存且不持久化空列表() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        MarketRealtimeRedisStore redisStore = mock(MarketRealtimeRedisStore.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of("1.000001"));
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"data":{"diff":[
                          {"f2":350000,"f3":10,"f4":300,"f6":2000,"f12":"000001","f13":"1","f14":"上证指数"}
                        ]}}
                        """)
                .thenReturn("{\"data\":{\"diff\":[]}}");
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH).thenReturn(INDEX_FLASH);
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                redisStore, indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();
        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000001");

        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000001");
        verify(redisStore, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshRealtimeWithoutEstimates_部分指数缺失_保留旧快照不剔除() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        MarketRealtimeRedisStore redisStore = mock(MarketRealtimeRedisStore.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of("1.000001", "0.399001"));
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("""
                        {"data":{"diff":[
                          {"f2":350000,"f3":10,"f4":300,"f6":2000,"f12":"000001","f13":"1","f14":"上证指数"},
                          {"f2":12000,"f3":8,"f4":20,"f6":500,"f12":"399001","f13":"0","f14":"深证成指"}
                        ]}}
                        """)
                .thenReturn("""
                        {"data":{"diff":[
                          {"f2":351000,"f3":11,"f4":310,"f6":2100,"f12":"000001","f13":"1","f14":"上证指数"}
                        ]}}
                        """);
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH).thenReturn(INDEX_FLASH);
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                redisStore, indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();
        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000001", "0.399001");

        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000001", "0.399001");
        assertThat(cache.getIndices()).extracting(IndexRealtimeSnapshot::secid)
                .containsExactly("1.000001", "0.399001");
    }

    @Test
    void refreshRealtimeWithoutEstimates_同后缀多市场重复行_不因duplicateKey整批失败() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of("1.000001"));
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {"data":{"diff":[
                  {"f2":350000,"f3":10,"f4":300,"f6":2000,"f12":"000001","f14":"上证指数"},
                  {"f2":123400,"f3":5,"f4":20,"f6":200,"f12":"000001","f14":"平安银行"}
                ]}}
                """);
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH);
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                mock(MarketRealtimeRedisStore.class), indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000001");
    }

    @Test
    void refreshRealtimeWithoutEstimates_板块空diff_保留旧板块缓存() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        ThsIndexFlashClient indexFlashClient = mock(ThsIndexFlashClient.class);
        WatchedIndicesApi userConfigService = mock(WatchedIndicesApi.class);
        TrackedNavProductGateway products = mock(TrackedNavProductGateway.class);
        MarketRealtimeRedisStore redisStore = mock(MarketRealtimeRedisStore.class);
        when(userConfigService.findAllForRefresh()).thenReturn(List.of());
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("{\"data\":{\"diff\":[]}}")
                .thenReturn("{\"data\":{\"diff\":[]}}");
        when(indexFlashClient.fetchIndexFlashRaw()).thenReturn(INDEX_FLASH).thenReturn(INDEX_FLASH);
        when(push2Client.fetchSectorListRaw("f3"))
                .thenReturn("""
                        {"data":{"diff":[{"f3":100,"f6":1000.0,"f12":"BK0001","f14":"测试板块","f62":200.0}]}}
                        """)
                .thenReturn("{\"data\":{\"diff\":[]}}");
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, products, mock(MarketDataMetrics.class), CLOCK,
                redisStore, indexFlashClient);

        cache.refreshRealtimeWithoutEstimates();
        assertThat(cache.getSectors()).extracting("sectorCode").containsExactly("BK0001");

        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getSectors()).extracting("sectorCode").containsExactly("BK0001");
        verify(redisStore, times(1)).save(org.mockito.ArgumentMatchers.any());
    }

    private static TrackedNavProductGateway.TrackedProduct fund(String code, FundStatus ignoredStatus) {
        return new TrackedNavProductGateway.TrackedProduct(null, 1L, code, null, null, null);
    }

    private static TrackedNavProductGateway.TrackedProduct fund(
            String code, TrackedNavProductGateway.InvestmentTarget investmentTarget) {
        return new TrackedNavProductGateway.TrackedProduct(null, code.hashCode(), code, null, null, investmentTarget);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;

        private MutableClock(Instant initialInstant) {
            this.instant = new AtomicReference<>(initialInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant.get();
        }

        private void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }
    }
}
