package com.fundpilot.backend.productcatalog.adapter.scheduler.feerefresh;

import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FundFeeRefreshJobTest {
    @Test
    void refreshesKnownSchedulesAtBeijingTwoThirty() throws Exception {
        FundFeeCommandHandler commands = mock(FundFeeCommandHandler.class);
        FundFeeRefreshJob job = new FundFeeRefreshJob(commands);

        job.refreshDaily();

        verify(commands).refreshKnownSchedules();
        Scheduled scheduled = FundFeeRefreshJob.class.getMethod("refreshDaily").getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 30 2 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
