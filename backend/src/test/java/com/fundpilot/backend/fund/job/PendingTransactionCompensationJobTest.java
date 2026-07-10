package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.PendingTransactionCompensationService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PendingTransactionCompensationJobTest {

    @Test
    void startupAndHourly_均触发补偿且时区为上海() throws Exception {
        PendingTransactionCompensationService service = mock(PendingTransactionCompensationService.class);
        PendingTransactionCompensationJob job = new PendingTransactionCompensationJob(service);

        job.compensateOnStartup();
        job.compensateHourly();

        verify(service, org.mockito.Mockito.times(2)).compensateAll();
        Method method = PendingTransactionCompensationJob.class.getMethod("compensateHourly");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 5 * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
