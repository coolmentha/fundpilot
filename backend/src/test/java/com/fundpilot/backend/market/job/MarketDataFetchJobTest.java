package com.fundpilot.backend.market.job;

import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.signal.job.SignalGenerationJob;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * issue #7 循环 G:{@code MarketDataFetchJob} 三个定时方法分别调
 * {@code fetchBatch(0/1/2)},对应 14:30/14:40/14:50 三批。
 * <p>{@code @Scheduled} 注解本身由 Spring 容器在运行时触发,这里只验证方法委托正确。
 */
class MarketDataFetchJobTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-10T06:30:00Z"), ZoneOffset.UTC);

    @Test
    void fetchBatch0_委托给_service_fetchBatch_0() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        TradingCalendarService calendar = tradingDayCalendar();
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob, calendar, CLOCK);

        job.fetchBatch0();

        verify(service, times(1)).fetchBatch(0);
        verify(signalJob, never()).generateDaily();
        verifyNoMoreInteractions(service);
    }

    @Test
    void fetchBatch1_委托给_service_fetchBatch_1() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        TradingCalendarService calendar = tradingDayCalendar();
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob, calendar, CLOCK);

        job.fetchBatch1();

        verify(service, times(1)).fetchBatch(1);
        verify(signalJob, never()).generateDaily();
        verifyNoMoreInteractions(service);
    }

    @Test
    void fetchBatch2_委托给_service_fetchBatch_2() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        TradingCalendarService calendar = tradingDayCalendar();
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob, calendar, CLOCK);

        job.fetchBatch2();

        var ordered = inOrder(service, signalJob);
        ordered.verify(service).fetchBatch(2);
        ordered.verify(signalJob).generateDaily();
        ordered.verifyNoMoreInteractions();
    }

    @Test
    void 非交易日_三批均跳过且不生成信号() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.isTradingDay(any())).thenReturn(false);
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob, calendar, CLOCK);

        job.fetchBatch0();
        job.fetchBatch1();
        job.fetchBatch2();

        verifyNoInteractions(service, signalJob);
    }

    private static TradingCalendarService tradingDayCalendar() {
        TradingCalendarService calendar = mock(TradingCalendarService.class);
        when(calendar.isTradingDay(any())).thenReturn(true);
        return calendar;
    }
}
