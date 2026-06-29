package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.NavConfirmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 净值确认定时任务(issue #15):每晚 21:00 回填当天 PENDING 交易的 nav + 另一侧 + confirmTime,转 CONFIRMED。
 * <p>cron {@code 0 0 21 * * MON-FRI} = 工作日 21:00:00 触发(净值公布后约 20:00-21:00)。
 * 依赖 {@code @EnableScheduling}(见 #7 启动类)。
 */
@Component
public class NavConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(NavConfirmJob.class);

    private final NavConfirmService navConfirmService;

    public NavConfirmJob(NavConfirmService navConfirmService) {
        this.navConfirmService = navConfirmService;
    }

    @Scheduled(cron = "0 0 21 * * MON-FRI")
    public void run() {
        log.info("净值确认任务开始");
        int confirmed = navConfirmService.confirmPendingTransactions(null);
        log.info("净值确认任务结束 confirmed={}", confirmed);
    }
}
