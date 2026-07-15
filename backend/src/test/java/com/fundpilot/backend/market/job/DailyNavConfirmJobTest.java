package com.fundpilot.backend.market.job;

import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.market.service.DailyNavConfirmService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyNavConfirmJobTest {

    @Test
    void confirmTodayNav_cron明确使用上海时区() throws Exception {
        Method method = DailyNavConfirmJob.class.getMethod("confirmTodayNav");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }

    @Test
    void catchUpPreviousTradingDayNav_每十分钟补拉上一交易日() throws Exception {
        DailyNavConfirmService confirmService = mock(DailyNavConfirmService.class);
        TradingCalendarService calendarService = mock(TradingCalendarService.class);
        Instant now = Instant.parse("2026-07-15T01:00:00Z");
        Instant today = Instant.parse("2026-07-15T00:00:00Z");
        Instant previousTradingDay = Instant.parse("2026-07-14T00:00:00Z");
        when(calendarService.latestTradingDayBefore(today)).thenReturn(Optional.of(previousTradingDay));
        DailyNavConfirmJob job = new DailyNavConfirmJob(
                confirmService, calendarService, Clock.fixed(now, ZoneOffset.UTC));

        job.catchUpPreviousTradingDayNav();

        verify(confirmService).confirmNavForDate(previousTradingDay);
        Method method = DailyNavConfirmJob.class.getMethod("catchUpPreviousTradingDayNav");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 */10 0-9 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
