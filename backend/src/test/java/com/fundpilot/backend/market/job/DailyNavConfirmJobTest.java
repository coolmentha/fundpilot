package com.fundpilot.backend.marketdata.adapter.scheduler.navpublishing;

import com.fundpilot.backend.marketdata.application.command.navpublishing.DailyNavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
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

class DailyNavPublishingJobTest {

    @Test
    void confirmTodayNav_cron明确使用上海时区() throws Exception {
        Method method = DailyNavPublishingJob.class.getMethod("publishToday");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
        assertThat(scheduled.cron()).isEqualTo("0 */5 20-22 * * MON-FRI");
    }

    @Test
    void catchUpPreviousTradingDayNav_每十分钟补拉上一交易日() throws Exception {
        DailyNavPublishingCommandHandler navPublishing = mock(DailyNavPublishingCommandHandler.class);
        TradingCalendarQueryHandler calendar = mock(TradingCalendarQueryHandler.class);
        Instant now = Instant.parse("2026-07-15T01:00:00Z");
        Instant today = Instant.parse("2026-07-15T00:00:00Z");
        Instant previousTradingDay = Instant.parse("2026-07-14T00:00:00Z");
        when(calendar.latestBefore(today)).thenReturn(Optional.of(previousTradingDay));
        DailyNavPublishingJob job = new DailyNavPublishingJob(
                navPublishing, calendar, Clock.fixed(now, ZoneOffset.UTC));

        job.catchUpPreviousTradingDay();

        verify(navPublishing).publishForDate(previousTradingDay);
        Method method = DailyNavPublishingJob.class.getMethod("catchUpPreviousTradingDay");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 */10 0-9 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
