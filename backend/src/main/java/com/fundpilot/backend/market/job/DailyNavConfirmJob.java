package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.DailyNavConfirmService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 当晚净值确认定时任务(issue #39):20:00-23:00 每 5 分钟轮询,
 * 确认当日净值落库(场外基金当日净值收盘后约 20:00 才公布)。
 * <p>cron 每 5 分钟触发一次，范围为周一到周五北京时间 20:00-22:59。
 */
@Component
@RequiredArgsConstructor
public class DailyNavConfirmJob {

    private final DailyNavConfirmService dailyNavConfirmService;

    @Scheduled(cron = "0 */5 20-22 * * MON-FRI", zone = "Asia/Shanghai")
    public void confirmTodayNav() {
        dailyNavConfirmService.confirmTodayNav();
    }
}
