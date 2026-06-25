package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.MarketDataFetchService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 行情指标拉取定时任务(issue #7):每日 14:30/14:40/14:50 三批拉取所有 EFFECTIVE 策略
 * 基金的当日行情指标,落 {@code market_indicator_snapshot}。分批避免单次跑不完。
 * <p>cron 表达式 {@code 0 30 14 * * MON-FRI} = 周一到周五 14:30:00 触发(服务器本地时区)。
 */
@Component
public class MarketDataFetchJob {

    private final MarketDataFetchService marketDataFetchService;

    public MarketDataFetchJob(MarketDataFetchService marketDataFetchService) {
        this.marketDataFetchService = marketDataFetchService;
    }

    @Scheduled(cron = "0 30 14 * * MON-FRI")
    public void fetchBatch0() {
        marketDataFetchService.fetchBatch(0);
    }

    @Scheduled(cron = "0 40 14 * * MON-FRI")
    public void fetchBatch1() {
        marketDataFetchService.fetchBatch(1);
    }

    @Scheduled(cron = "0 50 14 * * MON-FRI")
    public void fetchBatch2() {
        marketDataFetchService.fetchBatch(2);
    }
}
