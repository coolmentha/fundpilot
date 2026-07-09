package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.TradingCalendarSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 交易日历定时同步任务(task 07-09)。
 * <p>每日 04:00(UTC)从新浪同步交易日历,确保 trading_calendar 表覆盖到当年底(可前瞻)。
 * 启动时(ApplicationReadyEvent)预热一次,确保新部署/换环境后表非空(原同步无 @Scheduled,
 * 换环境忘同步会导致 DCA/信号/实时刷新全失效且无告警)。
 * <p>同步失败不阻塞:记 warn,保留旧数据(与原"同步失败"等价)。
 */
@Component
public class TradingCalendarSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TradingCalendarSyncJob.class);

    private final TradingCalendarSyncService tradingCalendarSyncService;

    public TradingCalendarSyncJob(TradingCalendarSyncService tradingCalendarSyncService) {
        this.tradingCalendarSyncService = tradingCalendarSyncService;
    }

    /**
     * 每日 04:00(UTC)同步交易日历。12:00 北京时间,盘前完成。
     * 新浪数据覆盖到当年底,节假日安排前一年 11 月发布,每日 1 次足够。
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void syncDaily() {
        try {
            int added = tradingCalendarSyncService.sync();
            log.info("交易日历每日同步完成,新增 {} 条", added);
        } catch (RuntimeException e) {
            log.warn("交易日历每日同步异常,保留旧数据: {}", e.getMessage());
        }
    }

    /**
     * 启动时预热:确保新部署/换环境后 trading_calendar 表非空。
     * 失败不阻塞启动(DCA/信号等降级为保守判定非交易日)。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            int added = tradingCalendarSyncService.sync();
            log.info("交易日历启动预热完成,新增 {} 条", added);
        } catch (RuntimeException e) {
            log.warn("交易日历启动预热失败,isTradingDay 将保守判定: {}", e.getMessage());
        }
    }
}
