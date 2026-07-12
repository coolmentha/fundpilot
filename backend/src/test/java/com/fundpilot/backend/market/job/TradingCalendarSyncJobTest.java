package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.TradingCalendarSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TradingCalendarSyncJobTest {

    @Test
    void deploymentValidationMode_skipsStartupWrite() {
        TradingCalendarSyncService service = mock(TradingCalendarSyncService.class);
        TradingCalendarSyncJob job = new TradingCalendarSyncJob(service);
        ReflectionTestUtils.setField(job, "syncOnStartup", true);
        ReflectionTestUtils.setField(job, "deploymentValidationMode", true);

        job.onApplicationReady();

        verifyNoInteractions(service);
    }
}
