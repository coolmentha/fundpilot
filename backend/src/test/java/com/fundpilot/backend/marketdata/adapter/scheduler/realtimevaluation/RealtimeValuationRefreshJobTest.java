package com.fundpilot.backend.marketdata.adapter.scheduler.realtimevaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.command.realtimevaluation.RealtimeValuationRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class RealtimeValuationRefreshJobTest {
    @Test
    void 交易时段按北京时间日期刷新完整行情() {
        var commands = mock(RealtimeValuationRefreshCommandHandler.class);
        var calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(Instant.parse("2026-07-06T00:00:00Z"))).thenReturn(true);
        var job = new RealtimeValuationRefreshJob(commands, calendar,
                Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC));

        job.refreshRealtime();

        verify(commands).refreshRealtimeWithoutEstimates();
    }

    @Test
    void 晚间仅刷新QDII估值() {
        var commands = mock(RealtimeValuationRefreshCommandHandler.class);
        var calendar = mock(TradingCalendarQueryHandler.class);
        var job = new RealtimeValuationRefreshJob(commands, calendar,
                Clock.fixed(Instant.parse("2026-07-06T14:00:00Z"), ZoneOffset.UTC));

        job.refreshFundEstimates();

        verify(commands).refreshQdiiFundEstimates();
    }

    @Test
    void A股交易时段刷新全部基金估值() {
        var commands = mock(RealtimeValuationRefreshCommandHandler.class);
        var calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(true);
        var job = new RealtimeValuationRefreshJob(commands, calendar,
                Clock.fixed(Instant.parse("2026-07-06T02:00:00Z"), ZoneOffset.UTC));

        job.refreshFundEstimates();

        verify(commands).refreshFundEstimates();
        verify(commands, never()).refreshQdiiFundEstimates();
    }

    @Test
    void 估值专用调度覆盖晚间和跨夜窗口() throws Exception {
        Scheduled[] schedules = RealtimeValuationRefreshJob.class.getDeclaredMethod("refreshFundEstimates")
                .getAnnotationsByType(Scheduled.class);
        assertThat(java.util.Arrays.stream(schedules).map(Scheduled::cron)).containsExactlyInAnyOrder(
                "*/30 * 9-23 * * MON-FRI", "*/30 * 0-5 * * TUE-SAT");
    }
}
