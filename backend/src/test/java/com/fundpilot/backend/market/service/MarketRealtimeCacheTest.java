package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.EastmoneyPush2Client;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.MarketBreadthSnapshot;
import com.fundpilot.backend.user.service.UserConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketRealtimeCacheTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T05:30:00Z"), ZoneOffset.UTC);

    @Test
    void refreshRealtimeWithoutEstimates_一次请求同时刷新自选指数和市场宽度() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        when(userConfigService.getWatchedIndices()).thenReturn(List.of("1.000300"));
        when(push2Client.fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString())).thenReturn("""
                {"data":{"diff":[
                  {"f2":400000,"f3":20,"f4":800,"f6":1000,"f12":"000300","f14":"沪深300"},
                  {"f2":350000,"f3":10,"f4":300,"f6":2000,"f12":"000001","f14":"上证指数","f104":1542,"f105":763},
                  {"f2":120000,"f3":30,"f4":400,"f6":3000,"f12":"399001","f14":"深证成指","f104":2012,"f105":872},
                  {"f2":150000,"f3":40,"f4":500,"f6":4000,"f12":"899050","f14":"北证50","f104":260,"f105":66}
                ]}}
                """);
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshRealtimeWithoutEstimates();

        verify(push2Client).fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.argThat(secids ->
                secids.contains("1.000300")
                        && secids.contains("1.000001")
                        && secids.contains("0.399001")
                        && secids.contains("0.899050")));
        assertThat(cache.getIndices()).extracting("secid").containsExactly("1.000300");
        assertThat(cache.getBreadth()).isEqualTo(new MarketBreadthSnapshot(3814, 1701));
    }

    @Test
    void refreshRealtimeWithoutEstimates_残缺市场数据保留旧宽度缓存() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
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
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshRealtimeWithoutEstimates();
        cache.refreshRealtimeWithoutEstimates();

        assertThat(cache.getBreadth()).isEqualTo(new MarketBreadthSnapshot(330, 140));
    }

    @Test
    void refreshAll_基金估值覆盖持仓和观察池基金() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());

        FundEntity holding = fund("510300", FundStatus.HOLDING);
        FundEntity watching = fund("159825", FundStatus.PENDING_HOLDING);
        when(fundRepository.findAll()).thenReturn(List.of(holding, watching));
        when(estimateService.fetchEstimateResult("510300")).thenReturn(FundEstimateResult.unavailable());
        when(estimateService.fetchEstimateResult("159825")).thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshAll();

        verify(fundRepository).findAll();
        verify(estimateService).fetchEstimateResult("510300");
        verify(estimateService).fetchEstimateResult("159825");
    }

    @Test
    void refreshFundEstimates_只刷新基金估值() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundEntity fund = fund("270042", FundStatus.HOLDING);
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult("270042")).thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshFundEstimates();

        verify(estimateService).fetchEstimateResult("270042");
        verify(push2Client, never()).fetchIndexRealtimeRaw(org.mockito.ArgumentMatchers.anyString());
        verify(push2Client, never()).fetchSectorListRaw(org.mockito.ArgumentMatchers.anyString());
        verify(push2Client, never()).fetchNorthboundRaw();
    }

    @Test
    void onApplicationReady_不在启动线程逐只刷新基金估值() throws Exception {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        assertThat(MarketRealtimeCache.class.getDeclaredMethod("onApplicationReady")
                .isAnnotationPresent(Async.class)).isTrue();
        cache.onApplicationReady();

        verify(fundRepository, never()).findAll();
        verify(estimateService, never()).fetchEstimateResult(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void warmFundEstimatesAfterReady_异步预热全部基金估值() throws Exception {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundEntity fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 15:00", "2026-07-09");
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult("510300")).thenReturn(FundEstimateResult.available(snapshot));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

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
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundEntity fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult("510300"))
                .thenReturn(FundEstimateResult.available(snapshot))
                .thenReturn(FundEstimateResult.unavailable());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshAll();
        cache.refreshAll();

        assertThat(cache.getEstimates(List.of("510300"))).isEmpty();
        assertThat(cache.hasEstimateFetchFailed("510300")).isFalse();
        assertThat(cache.getEstimateStatus("510300")).isEqualTo(EstimateStatus.UNAVAILABLE);
    }

    @Test
    void refreshAll_成功后异常会删除旧估值并标记失败() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundEntity fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult("510300"))
                .thenReturn(FundEstimateResult.available(snapshot))
                .thenThrow(new IllegalStateException("timeout"));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshAll();
        cache.refreshAll();

        assertThat(cache.getEstimates(List.of("510300"))).isEmpty();
        assertThat(cache.hasEstimateFetchFailed("510300")).isTrue();
    }

    @Test
    void refreshAll_旧日期估值不进入缓存且后续当天估值可恢复() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundEntity fund = fund("510300", FundStatus.HOLDING);
        FundEstimateSnapshot stale = new FundEstimateSnapshot(
                new BigDecimal("0.0100"), "2026-07-09 15:00", "2026-07-08");
        FundEstimateSnapshot current = new FundEstimateSnapshot(
                new BigDecimal("0.0123"), "2026-07-10 13:30", "2026-07-09");
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(estimateService.fetchEstimateResult("510300"))
                .thenReturn(FundEstimateResult.available(stale))
                .thenReturn(FundEstimateResult.available(current));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

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
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundEntity fund = fund("000009", FundStatus.HOLDING);
        fund.setFundName("易方达天天理财货币A");
        fund.setInvestmentTarget(InvestmentTarget.MONEY_MARKET);
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository, mock(MarketDataMetrics.class), CLOCK);

        cache.refreshAll();

        verify(estimateService, never()).fetchEstimateResult(org.mockito.ArgumentMatchers.anyString());
        assertThat(cache.getEstimateStatus("000009")).isEqualTo(EstimateStatus.UNAVAILABLE);
        assertThat(cache.hasEstimateFetchFailed("000009")).isFalse();
    }

    private static FundEntity fund(String code, FundStatus status) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setStatus(status);
        return fund;
    }
}
