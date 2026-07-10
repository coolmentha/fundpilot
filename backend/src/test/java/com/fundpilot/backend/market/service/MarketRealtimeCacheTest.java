package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.EastmoneyPush2Client;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.user.service.UserConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketRealtimeCacheTest {

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
        when(estimateService.fetchEstimate("510300")).thenReturn(Optional.empty());
        when(estimateService.fetchEstimate("159825")).thenReturn(Optional.empty());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository);

        cache.refreshAll();

        verify(fundRepository).findAll();
        verify(estimateService).fetchEstimate("510300");
        verify(estimateService).fetchEstimate("159825");
    }

    @Test
    void onApplicationReady_不在启动线程逐只刷新基金估值() {
        EastmoneyPush2Client push2Client = mock(EastmoneyPush2Client.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        UserConfigService userConfigService = mock(UserConfigService.class);
        FundRepository fundRepository = mock(FundRepository.class);
        when(userConfigService.getWatchedIndices()).thenReturn(List.of());
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository);

        cache.onApplicationReady();

        verify(fundRepository, never()).findAll();
        verify(estimateService, never()).fetchEstimate(org.mockito.ArgumentMatchers.anyString());
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
        when(estimateService.fetchEstimate("510300")).thenReturn(Optional.of(snapshot));
        MarketRealtimeCache cache = new MarketRealtimeCache(
                push2Client, estimateService, userConfigService, fundRepository);

        var method = MarketRealtimeCache.class.getMethod("warmFundEstimatesAfterReady");

        assertThat(method.isAnnotationPresent(Async.class)).isTrue();
        assertThat(method.isAnnotationPresent(EventListener.class)).isTrue();
        method.invoke(cache);
        assertThat(cache.getEstimates(List.of("510300"))).containsEntry("510300", snapshot);
    }

    private static FundEntity fund(String code, FundStatus status) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setStatus(status);
        return fund;
    }
}
