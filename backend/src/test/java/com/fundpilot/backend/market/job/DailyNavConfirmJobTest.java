package com.fundpilot.backend.market.job;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DailyNavConfirmJobTest {

    @Test
    void confirmTodayNav_cron明确使用上海时区() throws Exception {
        Method method = DailyNavConfirmJob.class.getMethod("confirmTodayNav");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
        assertThat(scheduled.cron()).isEqualTo("0 */5 20-22 * * MON-FRI");
    }
}
