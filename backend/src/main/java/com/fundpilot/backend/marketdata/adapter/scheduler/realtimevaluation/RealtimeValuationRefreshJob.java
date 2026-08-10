package com.fundpilot.backend.marketdata.adapter.scheduler.realtimevaluation;

import com.fundpilot.backend.marketdata.application.command.realtimevaluation.RealtimeValuationRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class RealtimeValuationRefreshJob {
    private static final ZoneId TRADING_ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime MORNING_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime AFTERNOON_OPEN = LocalTime.of(13, 0);
    private static final LocalTime AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private final RealtimeValuationRefreshCommandHandler commands;
    private final TradingCalendarQueryHandler calendar;
    private final Clock clock;
    private final AtomicBoolean refreshingRealtime = new AtomicBoolean(false);

    @Scheduled(cron = "*/30 * 9-14 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshRealtime() {
        if (isTradingHours()) runRealtimeOnce(commands::refreshRealtimeWithoutEstimates);
    }

    @Scheduled(cron = "*/30 * 9-23 * * MON-FRI", zone = "Asia/Shanghai")
    @Scheduled(cron = "*/30 * 0-5 * * TUE-SAT", zone = "Asia/Shanghai")
    public void refreshFundEstimates() {
        if (isTradingHours()) {
            commands.refreshFundEstimates();
        } else {
            commands.refreshQdiiFundEstimates();
        }
    }

    private void runRealtimeOnce(Runnable refresh) {
        if (!refreshingRealtime.compareAndSet(false, true)) {
            log.info("上一轮指数/板块/资金刷新尚未完成，跳过本轮");
            return;
        }
        try {
            refresh.run();
        } catch (RuntimeException exception) {
            log.warn("实时行情刷新异常", exception);
        } finally {
            refreshingRealtime.set(false);
        }
    }

    private boolean isTradingHours() {
        LocalTime now = clock.instant().atZone(TRADING_ZONE).toLocalTime();
        boolean inSession = (now.compareTo(MORNING_OPEN) >= 0 && now.isBefore(MORNING_CLOSE))
                || (now.compareTo(AFTERNOON_OPEN) >= 0 && now.isBefore(AFTERNOON_CLOSE));
        return inSession && calendar.isTradingDay(ChinaTradingDate.toUtcDate(clock.instant()));
    }
}
