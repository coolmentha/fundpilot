package com.fundpilot.backend.accounting.adapter.scheduler.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PendingTransactionCompensationJobTest {

    @Test
    void startupAndHourly_均触发补偿且时区为上海() throws Exception {
        TransactionCompensationCommandHandler accounting = mock(TransactionCompensationCommandHandler.class);
        PendingTransactionCompensationJob job = new PendingTransactionCompensationJob(accounting);

        job.compensateOnStartup();
        job.compensateHourly();

        verify(accounting, org.mockito.Mockito.times(2)).compensateAll(org.mockito.ArgumentMatchers.any());
        Method method = PendingTransactionCompensationJob.class.getMethod("compensateHourly");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 5 * * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void deploymentValidationMode_skipsStartupWrite() {
        TransactionCompensationCommandHandler accounting = mock(TransactionCompensationCommandHandler.class);
        PendingTransactionCompensationJob job = new PendingTransactionCompensationJob(accounting);
        ReflectionTestUtils.setField(job, "deploymentValidationMode", true);

        job.compensateOnStartup();

        org.mockito.Mockito.verifyNoInteractions(accounting);
    }
}
