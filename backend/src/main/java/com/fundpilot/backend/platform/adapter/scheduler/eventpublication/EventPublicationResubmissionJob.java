package com.fundpilot.backend.platform.adapter.scheduler.eventpublication;

import java.time.Duration;

import org.springframework.modulith.events.FailedEventPublications;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.ResubmissionOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fundpilot.backend.platform.observability.JobName;

import lombok.RequiredArgsConstructor;

/**
 * 异步事件监听失败的重发兜底：定期扫 {@code event_publication} 表，把两类未闭环的事件重新投递——
 * <ul>
 * <li>超期未完成：进程崩溃等导致的卡死事件（completion_date 为空且超过年龄门槛）；</li>
 * <li>已失败：{@code @ApplicationModuleListener} 执行抛异常被标记 FAILED 的事件。</li>
 * </ul>
 * <p>{@code republish-outstanding-events-on-restart=true} 只覆盖重启场景，本任务补上运行期缺口。
 * 年龄门槛避免重发仍在执行中的事件；重发走 Modulith 原生投递链路，要求监听器幂等
 * （现有监听器如定投计划退休均为幂等更新）。
 */
@Component
@RequiredArgsConstructor
public class EventPublicationResubmissionJob {

    /** 距最后投递不足该时长的事件不重发，避免与正在执行的监听器重复消费。 */
    private static final Duration RESUBMIT_MIN_AGE = Duration.ofMinutes(10);

    private final IncompleteEventPublications incomplete;
    private final FailedEventPublications failed;

    @JobName("未完成事件重发")
    @Scheduled(cron = "0 */5 * * * *", zone = "Asia/Shanghai")
    public void resubmit() {
        incomplete.resubmitIncompletePublicationsOlderThan(RESUBMIT_MIN_AGE);
        failed.resubmit(ResubmissionOptions.defaults().withMinAge(RESUBMIT_MIN_AGE));
    }
}
