package com.fundpilot.backend.marketdata.adapter.scheduler.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indicatorrefresh.MarketIndicatorRefreshCommandHandler;
import com.fundpilot.backend.marketdata.application.query.tradingcalendar.TradingCalendarQueryHandler;
import com.fundpilot.backend.platform.observability.JobName;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MarketIndicatorRefreshJob {
    private final MarketIndicatorRefreshCommandHandler commands;
    private final TradingCalendarQueryHandler calendar;
    private final Clock clock;

    @JobName("市场指标刷新·第1批")
    @Scheduled(cron = "0 30 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshBatch0() { refresh(0); }

    @JobName("收盘K线刷新")
    @Scheduled(cron = "0 0 17-22 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshClosingKlines() {
        if (isTradingDay()) commands.refreshClosingKlines(ChinaTradingDate.toUtcDate(clock.instant()));
    }

    @JobName("市场指标刷新·第2批")
    @Scheduled(cron = "0 40 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshBatch1() { refresh(1); }

    @JobName("市场指标刷新·第3批")
    @Scheduled(cron = "0 50 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void refreshBatch2() {
        if (isTradingDay()) commands.refreshBatchAndPublishCompletion(2);
    }

    private void refresh(int batchNumber) {
        if (isTradingDay()) commands.refreshBatch(batchNumber);
    }

    private boolean isTradingDay() {
        return calendar.isTradingDay(ChinaTradingDate.toUtcDate(clock.instant()));
    }
}
