package com.fundpilot.backend.alerting.adapter.scheduler.ruleevaluation;

import com.fundpilot.backend.alerting.application.query.ruleevaluation.AlertRuleEvaluationQueryHandler;
import com.fundpilot.backend.platform.observability.JobName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 网格交易提醒的评估任务：在收盘前 14:50–14:59（北京时间）每分钟评估一次全部启用的网格规则。
 *
 * <p>为什么是窗口而不是单点：网格必须在 15:00 申购截止前给出可执行的提示，而用盘中估值判定就要求
 * 「这一分钟确实跑到了」。窗口内每分钟重跑让补跑变得不必要——第一条成功发送后，同规则当日由通知表的
 * 唯一索引与 {@code alreadySent} 幂等拦下，因此进程在窗口中途恢复（或某分钟发送失败）都能自愈，
 * 不需要额外记录「今天是否跑过」。
 *
 * <p>只评估网格规则，其余规则仍由 {@link AlertEvaluationJob} 在 14:30 评估。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "fundpilot.alerting.enabled", havingValue = "true", matchIfMissing = true)
public class GridAlertEvaluationJob {

    private final AlertRuleEvaluationQueryHandler evaluations;

    @JobName("网格提醒评估")
    @Scheduled(cron = "${fundpilot.alerting.grid-evaluation-cron:0 50-59 14 * * MON-FRI}", zone = "Asia/Shanghai")
    public void evaluateGrid() {
        var result = evaluations.evaluateGrid();
        if (result.evaluatedRules() > 0) {
            log.info("网格提醒任务完成 evaluated={} matched={} sent={} failed={}", result.evaluatedRules(),
                    result.matchedRules(), result.sentRules(), result.failedRules());
        }
    }
}
