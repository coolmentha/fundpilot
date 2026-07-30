package com.fundpilot.backend.investmentplan.adapter.scheduler.planexecution;

import com.fundpilot.backend.investmentplan.application.command.planexecution.InvestmentPlanExecutionCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 开关为 true 时启用新链路；legacy 调度器仅在 false 的回切场景启用。 */
@Component
@ConditionalOnProperty(name = "fundpilot.investment-plan-scheduler.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class InvestmentPlanExecutionJob {
    private final InvestmentPlanQueryHandler queries;
    private final InvestmentPlanExecutionCommandHandler commands;
    private final Clock clock;

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
