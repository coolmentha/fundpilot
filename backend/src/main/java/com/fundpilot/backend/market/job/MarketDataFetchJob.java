package com.fundpilot.backend.market.job;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.market.service.MarketDataFetchService;
import com.fundpilot.backend.signal.job.SignalGenerationJob;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * 行情指标拉取定时任务(issue #7):每日 14:30/14:40/14:50 三批拉取所有 EFFECTIVE 策略
 * 基金的当日行情指标,落 {@code market_indicator_snapshot}。分批避免单次跑不完。
 * <p>cron 表达式 {@code 0 30 14 * * MON-FRI} = 周一到周五北京时间 14:30:00 触发。
 */
@Component
@RequiredArgsConstructor
public class MarketDataFetchJob {

    private final MarketDataFetchService marketDataFetchService;
    private final SignalGenerationJob signalGenerationJob;
    private final TradingCalendarService tradingCalendarService;
    private final Clock clock;

    @Scheduled(cron = "0 30 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void fetchBatch0() {
        if (isTradingDay()) {
            marketDataFetchService.fetchBatch(0);
        }
    }

    @Scheduled(cron = "0 40 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void fetchBatch1() {
        if (isTradingDay()) {
            marketDataFetchService.fetchBatch(1);
        }
    }

    @Scheduled(cron = "0 50 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void fetchBatch2() {
        if (!isTradingDay()) {
            return;
        }
        marketDataFetchService.fetchBatch(2);
        signalGenerationJob.generateDaily();
    }

    private boolean isTradingDay() {
        return tradingCalendarService.isTradingDay(ChinaTradingDate.toUtcDate(clock.instant()));
    }
}
