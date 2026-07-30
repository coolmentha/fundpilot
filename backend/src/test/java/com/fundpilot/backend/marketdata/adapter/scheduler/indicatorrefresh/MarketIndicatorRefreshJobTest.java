package com.fundpilot.backend.marketdata.adapter.scheduler.indicatorrefresh;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

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
    }

    private static TradingCalendarQueryHandler tradingDayCalendar() {
        var calendar = mock(TradingCalendarQueryHandler.class);
        when(calendar.isTradingDay(any())).thenReturn(true);
        return calendar;
    }
}
