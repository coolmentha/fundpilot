package com.fundpilot.backend.marketdata.adapter.scheduler.navpublishing;

import com.fundpilot.backend.marketdata.application.command.navpublishing.DailyNavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyNavPublishingJob {
    private final DailyNavPublishingCommandHandler navPublishing;
    private final TradingCalendarQueryHandler tradingCalendar;
    private final Clock clock;

    @Scheduled(cron = "0 */5 20-22 * * MON-FRI", zone = "Asia/Shanghai")
    public void publishToday() {
        navPublishing.publishToday();
    }

    @Scheduled(cron = "0 */10 0-9 * * *", zone = "Asia/Shanghai")
    public void catchUpPreviousTradingDay() {
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());
        tradingCalendar.latestBefore(today).ifPresentOrElse(navPublishing::publishForDate,
                () -> log.warn("上一交易日净值补拉跳过:交易日历缺少 {} 之前的交易日", today));
    }
}
