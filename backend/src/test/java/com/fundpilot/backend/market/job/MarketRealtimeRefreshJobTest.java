package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.entity.TradingCalendarEntity;
import com.fundpilot.backend.market.repository.TradingCalendarRepository;
import com.fundpilot.backend.market.service.MarketRealtimeCache;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    @Test
    void 晚间只刷新基金估值且不查询中国交易日历() {
        MarketRealtimeCache cache = mock(MarketRealtimeCache.class);
        TradingCalendarRepository repository = mock(TradingCalendarRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T14:00:00Z"), ZoneOffset.UTC);
        MarketRealtimeRefreshJob job = new MarketRealtimeRefreshJob(cache, repository, clock);

        job.refreshFundEstimates();

        verify(cache).refreshFundEstimates();
        verifyNoInteractions(repository);
    }

    @Test
    void 中国节假日A股时段仍刷新境外基金估值() {
        MarketRealtimeCache cache = mock(MarketRealtimeCache.class);
        TradingCalendarRepository repository = mock(TradingCalendarRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC);
        when(repository.findByCalendarDate(Instant.parse("2026-07-06T00:00:00Z")))
                .thenReturn(Optional.empty());
        MarketRealtimeRefreshJob job = new MarketRealtimeRefreshJob(cache, repository, clock);

        job.refreshFundEstimates();

        verify(cache).refreshFundEstimates();
    }

    @Test
    void A股交易时段估值专用刷新不重复请求() {
        MarketRealtimeCache cache = mock(MarketRealtimeCache.class);
        TradingCalendarRepository repository = mock(TradingCalendarRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC);
        TradingCalendarEntity calendar = new TradingCalendarEntity();
        calendar.setTradingDay(true);
        when(repository.findByCalendarDate(Instant.parse("2026-07-06T00:00:00Z")))
                .thenReturn(Optional.of(calendar));
        MarketRealtimeRefreshJob job = new MarketRealtimeRefreshJob(cache, repository, clock);

        job.refreshFundEstimates();

        verify(cache, never()).refreshFundEstimates();
    }

    @Test
    void 估值专用调度覆盖晚间和跨夜窗口() throws Exception {
        Scheduled[] schedules = MarketRealtimeRefreshJob.class.getDeclaredMethod("refreshFundEstimates")
                .getAnnotationsByType(Scheduled.class);

        assertThat(java.util.Arrays.stream(schedules).map(Scheduled::cron))
                .containsExactlyInAnyOrder(
                        "*/30 * 9-23 * * MON-FRI",
                        "*/30 * 0-5 * * TUE-SAT");
    }
}
