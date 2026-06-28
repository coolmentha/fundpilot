package com.fundpilot.backend.market.job;

import com.fundpilot.backend.market.service.DailyNavConfirmService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 当晚净值确认定时任务(issue #39):20:00-23:00 每分钟轮询,
 * 确认当日净值落库(场外基金当日净值收盘后约 20:00 才公布)。
 * <p>cron {@code 0 * 20-22 * * MON-FRI} = 周一到周五 20:00-22:59 每分钟触发(服务器本地时区)。
 * 已确认的基金跳过(天然停止条件),全部确认后该分钟空跑。
 */
@Component
public class DailyNavConfirmJob {

    private final DailyNavConfirmService dailyNavConfirmService;

    public DailyNavConfirmJob(DailyNavConfirmService dailyNavConfirmService) {
        this.dailyNavConfirmService = dailyNavConfirmService;
    }

    @Scheduled(cron = "0 * 20-22 * * MON-FRI")
    public void confirmTodayNav() {
        dailyNavConfirmService.confirmTodayNav();
    }
}
