package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.NavConfirmService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NavConfirmJobTest {

    @Mock
    private NavConfirmService navConfirmService;

    @Test
    void run_北京时间周二凌晨传周一Utc零点给确认服务() {
        // 生产 cron 在 Asia/Shanghai 周二 03:00 触发,对应 UTC 周一 19:00。
        Instant now = Instant.parse("2026-07-13T19:00:00Z");
        NavConfirmJob job = new NavConfirmJob(navConfirmService, Clock.fixed(now, ZoneOffset.UTC));
        Instant expected = Instant.parse("2026-07-13T00:00:00Z");

        job.run();

        ArgumentCaptor<Instant> date = ArgumentCaptor.forClass(Instant.class);
        verify(navConfirmService).confirmPendingTransactions(date.capture());
        assertThat(date.getValue()).isEqualTo(expected);
    }
}
