package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.TradingCalendarSyncService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 交易日历定时同步任务(task 07-09)。
 * <p>每日北京时间 04:00 从新浪同步交易日历。
 * 启动时(ApplicationReadyEvent)预热一次,确保新部署/换环境后表非空(原同步无 @Scheduled,
 * 换环境忘同步会导致 DCA/信号/实时刷新全失效且无告警)。
 * <p>同步失败不阻塞:记 warn,保留旧数据(与原"同步失败"等价)。
 * <p>预热可用 {@code trading-calendar.sync-on-startup=false} 关闭(集成测试环境用,
 * 避免预热灌入 8000+ 条数据与测试夹具日期撞唯一索引)。
 */
@Component
@RequiredArgsConstructor
public class TradingCalendarSyncJob {

    private static final Logger log = LoggerFactory.getLogger(TradingCalendarSyncJob.class);

    private final TradingCalendarSyncService tradingCalendarSyncService;
    @Value("${trading-calendar.sync-on-startup:true}")
    private boolean syncOnStartup;

    @Value("${fundpilot.deployment.validation-mode:false}")
    private boolean deploymentValidationMode;

    /**
     * 每日北京时间 04:00 同步交易日历。
     * 新浪数据覆盖到当年底,节假日安排前一年 11 月发布,每日 1 次足够。
     */
    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Shanghai")
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
     * 测试环境用 {@code trading-calendar.sync-on-startup=false} 关闭,避免灌入数据撞测试夹具。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (deploymentValidationMode) {
            log.info("部署候选验证模式已启用,跳过交易日历启动写入");
            return;
        }
        if (!syncOnStartup) {
            log.info("交易日历启动预热已关闭(sync-on-startup=false)");
            return;
        }
        try {
            int added = tradingCalendarSyncService.sync();
            log.info("交易日历启动预热完成,新增 {} 条", added);
        } catch (RuntimeException e) {
            log.warn("交易日历启动预热失败,isTradingDay 将保守判定: {}", e.getMessage());
        }
    }
}
