package com.fundpilot.backend.marketdata.adapter.scheduler.tradingcalendar;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fundpilot.backend.marketdata.application.command.tradingcalendar.TradingCalendarCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TradingCalendarSynchronizationJobTest {
    @Test
    void deploymentValidationModeSkipsStartupWrite() {
        TradingCalendarCommandHandler commands = mock(TradingCalendarCommandHandler.class);
        TradingCalendarSynchronizationJob job = new TradingCalendarSynchronizationJob(commands);
        ReflectionTestUtils.setField(job, "syncOnStartup", true);
        ReflectionTestUtils.setField(job, "deploymentValidationMode", true);

        job.onApplicationReady();

        verifyNoInteractions(commands);
    }
}
