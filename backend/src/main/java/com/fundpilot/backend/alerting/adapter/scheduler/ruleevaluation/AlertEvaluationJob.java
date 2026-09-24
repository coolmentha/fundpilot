package com.fundpilot.backend.alerting.adapter.scheduler.ruleevaluation;

import com.fundpilot.backend.alerting.application.query.ruleevaluation.AlertRuleEvaluationQueryHandler;
import com.fundpilot.backend.platform.observability.JobName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每个交易日 14:30（北京时间）评估一次全部启用的提醒规则。 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fundpilot.alerting.enabled", havingValue = "true", matchIfMissing = true)
public class AlertEvaluationJob {
    private final AlertRuleEvaluationQueryHandler evaluations;

    @JobName("提醒规则评估")
    @Scheduled(cron = "${fundpilot.alerting.evaluation-cron:0 30 14 * * MON-FRI}", zone = "Asia/Shanghai")
    public void evaluate() {
        var result = evaluations.evaluate();
        if (result.evaluatedRules() > 0) {
            log.info("价格提醒任务完成 evaluated={} matched={} sent={} failed={}", result.evaluatedRules(),
                    result.matchedRules(), result.sentRules(), result.failedRules());
        }
    }
}
