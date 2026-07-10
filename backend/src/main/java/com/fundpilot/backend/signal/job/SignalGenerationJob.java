package com.fundpilot.backend.signal.job;

import com.fundpilot.backend.signal.service.SignalGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 信号生成任务(issue #13):由 {@code MarketDataFetchJob.fetchBatch2} 在第三批行情完成后调用,
 * 遍历所有 EFFECTIVE 策略基金并落 SignalLog。自身不再声明独立 cron,避免同秒调度竞争。
 */
@Component
public class SignalGenerationJob {

    private static final Logger log = LoggerFactory.getLogger(SignalGenerationJob.class);

    private final SignalGenerationService signalGenerationService;

    public SignalGenerationJob(SignalGenerationService signalGenerationService) {
        this.signalGenerationService = signalGenerationService;
    }

    public void generateDaily() {
        Instant now = Instant.now();
        log.info("信号生成任务启动 date={}", now);
        signalGenerationService.generateDailySignals(now);
        log.info("信号生成任务完成 date={}", now);
    }
}
