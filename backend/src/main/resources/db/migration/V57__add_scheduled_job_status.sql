-- 定时任务最近一次执行状态:按任务键 upsert,每个 @Scheduled 方法一行。
-- 用途:任务连续失败时在 /admin「系统监控」可见,不依赖已停用的 Prometheus/Grafana。
-- 该表是纯运行态快照:行数恒等于任务数(约 20),无业务生命周期,故不设 version/deleted_date。
CREATE TABLE scheduled_job_status (
    task_key VARCHAR(200) PRIMARY KEY,
    last_started_at TIMESTAMPTZ NOT NULL,
    last_finished_at TIMESTAMPTZ NOT NULL,
    last_result VARCHAR(16) NOT NULL CHECK (last_result IN ('SUCCESS', 'FAILURE')),
    last_duration_ms BIGINT NOT NULL,
    consecutive_failures INTEGER NOT NULL DEFAULT 0,
    last_failure_at TIMESTAMPTZ,
    last_failure_message VARCHAR(512),
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_scheduled_job_status_failures
    ON scheduled_job_status (consecutive_failures DESC, last_finished_at DESC);