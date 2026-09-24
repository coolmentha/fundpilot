package com.fundpilot.backend.platform.observability;

import java.time.Instant;

/** 一次定时任务执行的结果报告;由 {@link JobMetricsAspect} 在 finally 中产出。 */
public record JobExecutionReport(String task, Instant startedAt, Instant finishedAt,
                                 long durationMillis, boolean success, String failureMessage) {
}