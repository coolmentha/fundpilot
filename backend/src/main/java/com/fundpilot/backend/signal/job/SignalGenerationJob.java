package com.fundpilot.backend.signal.job;

import com.fundpilot.backend.signal.service.SignalGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 信号生成定时任务(issue #13):每日 14:50(周一到周五)遍历所有 EFFECTIVE 策略基金,
 * 调 {@link SignalGenerationService#generateDailySignals} 生成信号并落 SignalLog。
 * <p>cron {@code 0 50 14 * * MON-FRI} = 周一到周五 14:50:00 触发(服务器本地时区)。
 * 主类 {@code @EnableScheduling} 已在 #7 启用。
 */
@Component
public class SignalGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationJob.class);

    private final SignalGenerationService signalGenerationService;

    public SignalGenerationJob(SignalGenerationService signalGenerationService) {
        this.signalGenerationService = signalGenerationService;
    }

    @Scheduled(cron = "0 50 14 * * MON-FRI")
    public void generateDaily() {
        Instant now = Instant.now();
        log.info("信号生成任务启动 date={}", now);
        signalGenerationService.generateDailySignals(now);
        log.info("信号生成任务完成 date={}", now);
    }
}
