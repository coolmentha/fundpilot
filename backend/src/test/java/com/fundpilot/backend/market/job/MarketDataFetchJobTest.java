package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.signal.job.SignalGenerationJob;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * issue #7 循环 G:{@code MarketDataFetchJob} 三个定时方法分别调
 * {@code fetchBatch(0/1/2)},对应 14:30/14:40/14:50 三批。
 * <p>{@code @Scheduled} 注解本身由 Spring 容器在运行时触发,这里只验证方法委托正确。
 */
class MarketDataFetchJobTest {

    @Test
    void fetchBatch0_委托给_service_fetchBatch_0() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob);

        job.fetchBatch0();

        verify(service, times(1)).fetchBatch(0);
        verify(signalJob, never()).generateDaily();
        verifyNoMoreInteractions(service);
    }

    @Test
    void fetchBatch1_委托给_service_fetchBatch_1() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob);

        job.fetchBatch1();

        verify(service, times(1)).fetchBatch(1);
        verify(signalJob, never()).generateDaily();
        verifyNoMoreInteractions(service);
    }

    @Test
    void fetchBatch2_委托给_service_fetchBatch_2() {
        MarketDataFetchService service = mock(MarketDataFetchService.class);
        SignalGenerationJob signalJob = mock(SignalGenerationJob.class);
        MarketDataFetchJob job = new MarketDataFetchJob(service, signalJob);

        job.fetchBatch2();

        var ordered = inOrder(service, signalJob);
        ordered.verify(service).fetchBatch(2);
        ordered.verify(signalJob).generateDaily();
        ordered.verifyNoMoreInteractions();
    }
}
