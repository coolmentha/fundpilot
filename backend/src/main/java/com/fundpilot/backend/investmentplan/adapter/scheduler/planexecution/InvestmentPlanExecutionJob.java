package com.fundpilot.backend.investmentplan.adapter.scheduler.planexecution;

import com.fundpilot.backend.investmentplan.application.command.planexecution.InvestmentPlanExecutionCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
import com.fundpilot.backend.platform.observability.JobName;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每个交易日 14:55（北京时间）执行全部已生效的定投计划。 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvestmentPlanExecutionJob {
    private final InvestmentPlanQueryHandler queries;
    private final InvestmentPlanExecutionCommandHandler commands;
    private final Clock clock;

    @JobName("投资计划执行")
    @Scheduled(cron = "0 55 14 * * MON-FRI", zone = "Asia/Shanghai")
    public void run() {
        var now = clock.instant();
        for (Long planId : queries.effectiveEnabledIds()) {
            try {
                commands.execute(planId, now);
            } catch (RuntimeException exception) {
                log.error("投资计划执行失败 plan_id={}", planId, exception);
            }
        }
    }
}
