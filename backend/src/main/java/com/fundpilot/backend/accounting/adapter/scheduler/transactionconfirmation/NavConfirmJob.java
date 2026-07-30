package com.fundpilot.backend.accounting.adapter.scheduler.transactionconfirmation;

import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import com.fundpilot.backend.accounting.application.command.transactionconfirmation.TransactionCompensationCommandHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * 净值确认定时任务(issue #15):次日凌晨 3:00 回填前一天 PENDING 交易的 nav + 另一侧 + confirmTime,转 CONFIRMED。
 * <p>cron {@code 0 0 3 * * MON-FRI} = 工作日凌晨 3:00 触发。
 * 时序:计划生成 PENDING → 当晚 DailyNavPublishingJob 发布单位净值 → 次日凌晨 3:00 本 Job 补偿确认。
 * 凌晨 3 点确认的是昨天及更早的 PENDING 流水(下单日净值已在昨晚落库),当天定投流水尚未生成,不冲突。
 * 依赖 {@code @EnableScheduling}(见 #7 启动类)。
 */
@Component
@RequiredArgsConstructor
public class NavConfirmJob {

    private static final Logger log = LoggerFactory.getLogger(NavConfirmJob.class);

    private final TransactionCompensationCommandHandler accountingCompensation;
    private final Clock clock;

    @Scheduled(cron = "0 0 3 * * MON-FRI", zone = "Asia/Shanghai")
    public void run() {
        log.info("净值确认任务开始");
        Instant tradeDay = ChinaTradingDate.previousUtcDate(clock.instant());
        int confirmed = accountingCompensation.compensateAll(tradeDay);
        log.info("净值确认任务结束 confirmed={}", confirmed);
    }
}
