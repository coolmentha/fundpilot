package com.fundpilot.backend.marketdata.adapter.scheduler.indicatorrefresh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class MarketIndicatorRefreshJobTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T06:30:00Z"), ZoneOffset.UTC);

    @Test
    void 三批交易日按顺序委派() {
        var commands = mock(MarketIndicatorRefreshCommandHandler.class);
        var calendar = tradingDayCalendar();
        var job = new MarketIndicatorRefreshJob(commands, calendar, CLOCK);

        job.refreshBatch0();
        job.refreshBatch1();
        job.refreshBatch2();

        verify(commands).refreshBatch(0);
        verify(commands).refreshBatch(1);
        verify(commands).refreshBatchAndPublishCompletion(2);
    }

    @Test
    void 非交易日不刷新() {
        var commands = mock(MarketIndicatorRefreshCommandHandler.class);
        var calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(false);
        var job = new MarketIndicatorRefreshJob(commands, calendar, CLOCK);

        job.refreshBatch0();
        job.refreshBatch1();
        job.refreshBatch2();

        verifyNoInteractions(commands);
        job.refreshClosingKlines();
        verifyNoInteractions(commands);
    }

    @Test
    void 收盘补拉北京时间17至22点每小时运行且仅委派K线() throws Exception {
        var commands = mock(MarketIndicatorRefreshCommandHandler.class);
        var job = new MarketIndicatorRefreshJob(commands, tradingDayCalendar(),
                Clock.fixed(Instant.parse("2026-07-10T09:00:00Z"), ZoneOffset.UTC));

        job.refreshClosingKlines();

        verify(commands).refreshClosingKlines(Instant.parse("2026-07-10T00:00:00Z"));
        org.mockito.Mockito.verifyNoMoreInteractions(commands);
        var scheduled = MarketIndicatorRefreshJob.class.getMethod("refreshClosingKlines")
                .getAnnotation(Scheduled.class);
        assertThat(scheduled.cron()).isEqualTo("0 0 17-22 * * MON-FRI");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }

    private static TradingCalendarQueryHandler tradingDayCalendar() {
        var calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(true);
        return calendar;
    }
}
