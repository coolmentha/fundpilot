package com.fundpilot.backend.platform.observability;

import java.time.Instant;

/** 定时任务最近一次执行状态;直接对应 {@code scheduled_job_status} 一行。 */
public record JobExecutionStatus(String task, Instant lastStartedAt, Instant lastFinishedAt,
                                 String lastResult, long lastDurationMillis, int consecutiveFailures,
                                 Instant lastFailureAt, String lastFailureMessage) {
}