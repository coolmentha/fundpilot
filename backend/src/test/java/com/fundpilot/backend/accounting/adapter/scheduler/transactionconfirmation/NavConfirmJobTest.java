package com.fundpilot.backend.accounting.adapter.scheduler.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NavConfirmJobTest {

    @Test
    void run_触发全量补偿确认() {
        TransactionCompensationCommandHandler accountingCompensation =
                mock(TransactionCompensationCommandHandler.class);
        NavConfirmJob job = new NavConfirmJob(accountingCompensation);

        job.run();

        verify(accountingCompensation).compensateAll();
    }

    @Test
    void run_cron明确使用上海时区() throws Exception {
        Method method = NavConfirmJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}