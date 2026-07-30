package com.fundpilot.backend.accounting.adapter.scheduler.transactionconfirmation;

import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.lang.reflect.Method;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class NavConfirmJobTest {

    @Test
    void run_北京时间周二凌晨传周一Utc零点给确认服务() {
        // 生产 cron 在 Asia/Shanghai 周二 03:00 触发,对应 UTC 周一 19:00。
        Instant now = Instant.parse("2026-07-13T19:00:00Z");
        TransactionCompensationCommandHandler accountingCompensation = org.mockito.Mockito.mock(
                TransactionCompensationCommandHandler.class);
        NavConfirmJob job = new NavConfirmJob(accountingCompensation, Clock.fixed(now, ZoneOffset.UTC));
        Instant expected = Instant.parse("2026-07-13T00:00:00Z");

        job.run();

        verify(accountingCompensation).compensateAll(expected);
    }

    @Test
    void run_cron明确使用上海时区() throws Exception {
        Method method = NavConfirmJob.class.getMethod("run");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
