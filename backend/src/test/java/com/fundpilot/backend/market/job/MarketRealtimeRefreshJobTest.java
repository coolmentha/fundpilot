package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import java.util.concurrent.atomic.AtomicReference;

class MarketRealtimeRefreshJobTest {

    @Test
    void 交易时段使用北京时间自然日对应的UTC日期标签() {
        MarketRealtimeCache cache = mock(MarketRealtimeCache.class);
        TradingCalendarRepository repository = mock(TradingCalendarRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC);
        TradingCalendarEntity calendar = new TradingCalendarEntity();
        calendar.setCalendarDate(Instant.parse("2026-07-06T00:00:00Z"));
        calendar.setTradingDay(true);
        when(repository.findByCalendarDate(Instant.parse("2026-07-06T00:00:00Z")))
                .thenReturn(Optional.of(calendar));
        MarketRealtimeRefreshJob job = new MarketRealtimeRefreshJob(cache, repository, clock);

        job.refreshRealtime();

        verify(repository).findByCalendarDate(Instant.parse("2026-07-06T00:00:00Z"));
        verify(cache).refreshAll();
    }

    @Test
    void 上一轮刷新未结束时跳过重入() {
        MarketRealtimeCache cache = mock(MarketRealtimeCache.class);
        TradingCalendarRepository repository = mock(TradingCalendarRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC);
        TradingCalendarEntity calendar = new TradingCalendarEntity();
        calendar.setCalendarDate(Instant.parse("2026-07-06T00:00:00Z"));
        calendar.setTradingDay(true);
        when(repository.findByCalendarDate(Instant.parse("2026-07-06T00:00:00Z")))
                .thenReturn(Optional.of(calendar));
        AtomicReference<MarketRealtimeRefreshJob> holder = new AtomicReference<>();
        org.mockito.Mockito.doAnswer(invocation -> {
            holder.get().refreshRealtime();
            return null;
        }).when(cache).refreshAll();
        MarketRealtimeRefreshJob job = new MarketRealtimeRefreshJob(cache, repository, clock);
        holder.set(job);

        job.refreshRealtime();

        verify(cache, times(1)).refreshAll();
    }
}
