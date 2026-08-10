package com.fundpilot.backend.marketdata.adapter.scheduler.tradingcalendar;

import com.fundpilot.backend.marketdata.application.command.tradingcalendar.TradingCalendarCommandHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TradingCalendarSynchronizationJob {
    private final TradingCalendarCommandHandler commands;
    @Value("${trading-calendar.sync-on-startup:true}") private boolean syncOnStartup;
    @Value("${fundpilot.deployment.validation-mode:false}") private boolean deploymentValidationMode;

    @Scheduled(cron = "0 0 4 * * *", zone = "Asia/Shanghai")
    public void synchronizeDaily() {
        synchronize("每日", true);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (deploymentValidationMode || !syncOnStartup) return;
        synchronize("启动预热", true);
    }

    private void synchronize(String source, boolean incremental) {
        try {
            log.info("交易日历{}同步完成,新增 {} 条", source, commands.synchronize(incremental));
        } catch (RuntimeException exception) {
            log.warn("交易日历{}同步异常,保留旧数据", source, exception);
        }
    }
}
