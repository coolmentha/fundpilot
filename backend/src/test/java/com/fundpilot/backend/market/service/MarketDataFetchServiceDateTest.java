package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundNavUpdatedEvent;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.repository.IndexKlineRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketDataFetchServiceDateTest {

    @Test
    void fetchOneFund_北京时间凌晨写入当天UTC日期标签() {
        FundRepository fundRepository = mock(FundRepository.class);
        FundNavHistoryRepository navRepository = mock(FundNavHistoryRepository.class);
        MarketDataSource marketDataSource = mock(MarketDataSource.class);
        MarketIndicatorSnapshotService snapshotService = mock(MarketIndicatorSnapshotService.class);
        IndexKlineRepository indexKlineRepository = mock(IndexKlineRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        MarketDataFetchService service = new MarketDataFetchService(
                fundRepository, navRepository, marketDataSource, snapshotService, indexKlineRepository, clock,
                eventPublisher);

        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(navRepository.findNavDatesByFundEntity_Id(1L)).thenReturn(List.of());
        when(marketDataSource.fetchNavHistory("510300")).thenReturn(List.of(
                nav("2026-07-03T00:00:00Z", "1.00"),
                nav("2026-07-06T00:00:00Z", "1.01")));
        when(snapshotService.upsert(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.fetchOneFund(1L);

        ArgumentCaptor<MarketIndicatorSnapshotEntity> captor =
                ArgumentCaptor.forClass(MarketIndicatorSnapshotEntity.class);
        verify(snapshotService).upsert(captor.capture());
        verify(eventPublisher).publishEvent(new FundNavUpdatedEvent(1L));
        assertThat(captor.getValue().getSnapshotDate())
                .isEqualTo(Instant.parse("2026-07-07T00:00:00Z"));
    }

    private static FundNavSnapshot nav(String date, String value) {
        BigDecimal nav = new BigDecimal(value);
        return new FundNavSnapshot(Instant.parse(date), nav, nav);
    }
}
