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
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
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
                new RequiresNewTransactionExecutor());

        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");
        Instant today = ChinaTradingDate.toUtcDate(Instant.now());
        when(fundRepository.findAll()).thenReturn(List.of(fund));
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(navRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L)).thenReturn(List.of());
        when(navRepository.findNavDatesByFundEntity_Id(1L)).thenReturn(List.of());
        when(estimateService.fetchEstimate("510300")).thenReturn(Optional.of(
                new FundEstimateSnapshot(BigDecimal.ZERO, "today", today.toString())));
        when(marketDataSource.fetchNavHistory("510300")).thenReturn(List.of(
                new FundNavSnapshot(today, new BigDecimal("1.25"), new BigDecimal("1.25"))));

        service.confirmTodayNav();

        verify(eventPublisher).publishEvent(new FundNavUpdatedEvent(1L));
    }
}
