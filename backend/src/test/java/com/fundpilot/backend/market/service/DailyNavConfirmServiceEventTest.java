package com.fundpilot.backend.market.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.common.RequiresNewTransactionExecutor;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundNavUpdatedEvent;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyNavConfirmServiceEventTest {

    @Test
    void confirmTodayNav_新净值落库后发布基金净值更新事件() {
        FundRepository fundRepository = mock(FundRepository.class);
        FundNavHistoryRepository navRepository = mock(FundNavHistoryRepository.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        MarketDataSource marketDataSource = mock(MarketDataSource.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        DailyNavConfirmService service = new DailyNavConfirmService(
                fundRepository, navRepository, estimateService, marketDataSource, eventPublisher,
                new RequiresNewTransactionExecutor(), Clock.fixed(today(), ZoneOffset.UTC));

        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");
        Instant today = ChinaTradingDate.toUtcDate(today());
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(navRepository.findNavDatesByFundEntity_Id(1L)).thenReturn(List.of());
        when(estimateService.fetchEstimate("510300")).thenReturn(Optional.of(
                new FundEstimateSnapshot(BigDecimal.ZERO, "today", today.toString())));
        when(marketDataSource.fetchNavHistory("510300")).thenReturn(List.of(
                new FundNavSnapshot(today, new BigDecimal("1.25"), new BigDecimal("1.25"))));

        service.confirmTodayNav();

        verify(eventPublisher).publishEvent(new FundNavUpdatedEvent(1L));
    }

    @Test
    void confirmNavForDate_上一交易日净值可跨夜补拉() {
        FundRepository fundRepository = mock(FundRepository.class);
        FundNavHistoryRepository navRepository = mock(FundNavHistoryRepository.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        MarketDataSource marketDataSource = mock(MarketDataSource.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        DailyNavConfirmService service = new DailyNavConfirmService(
                fundRepository, navRepository, estimateService, marketDataSource, eventPublisher,
                new RequiresNewTransactionExecutor(), Clock.fixed(today(), ZoneOffset.UTC));
        Instant previousTradingDay = Instant.parse("2026-07-14T00:00:00Z");
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(navRepository.findNavDatesByFundEntity_Id(1L)).thenReturn(List.of());
        when(estimateService.fetchEstimate("510300")).thenReturn(Optional.of(
                new FundEstimateSnapshot(BigDecimal.ZERO, "yesterday", previousTradingDay.toString())));
        when(marketDataSource.fetchNavHistory("510300")).thenReturn(List.of(
                new FundNavSnapshot(previousTradingDay, new BigDecimal("1.24"),
                        new BigDecimal("1.24"))));

        service.confirmNavForDate(previousTradingDay);

        verify(marketDataSource).fetchNavHistory("510300");
        verify(eventPublisher).publishEvent(new FundNavUpdatedEvent(1L));
    }

    @Test
    void confirmNavForDate_历史接口尚未包含目标日期时不发布更新事件() {
        FundRepository fundRepository = mock(FundRepository.class);
        FundNavHistoryRepository navRepository = mock(FundNavHistoryRepository.class);
        FundEstimateService estimateService = mock(FundEstimateService.class);
        MarketDataSource marketDataSource = mock(MarketDataSource.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        DailyNavConfirmService service = new DailyNavConfirmService(
                fundRepository, navRepository, estimateService, marketDataSource, eventPublisher,
                new RequiresNewTransactionExecutor(), Clock.fixed(today(), ZoneOffset.UTC));
        Instant targetDate = Instant.parse("2026-07-14T00:00:00Z");
        Instant olderDate = Instant.parse("2026-07-11T00:00:00Z");
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(estimateService.fetchEstimate("510300")).thenReturn(Optional.of(
                new FundEstimateSnapshot(BigDecimal.ZERO, "yesterday", targetDate.toString())));
        when(marketDataSource.fetchNavHistory("510300")).thenReturn(List.of(
                new FundNavSnapshot(olderDate, new BigDecimal("1.23"), new BigDecimal("1.23"))));

        service.confirmNavForDate(targetDate);

        verify(eventPublisher, never()).publishEvent(new FundNavUpdatedEvent(1L));
    }

    private static Instant today() {
        return Instant.parse("2026-07-15T01:00:00Z");
    }
}
