package com.fundpilot.backend.platform.observability;

import com.fundpilot.backend.platform.transaction.RequiresNewTransactionExecutor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 定时任务执行状态的持久化:按任务键 upsert,并供 /admin 读取。
 *
 * <p>写入走 {@link RequiresNewTransactionExecutor} 独立事务:切面运行在无事务保证的层,
 * 任务自身事务若回滚,失败记录不能一起回滚。
 * <p>写入异常一律吞掉并只记日志:该方法在切面的 {@code finally} 中调用,抛出会顶掉原始任务异常。
 */
@Component
@RequiredArgsConstructor
public class JobExecutionStatusStore {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionStatusStore.class);
    private static final int FAILURE_MESSAGE_MAX_LENGTH = 512;
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILURE = "FAILURE";

    private static final String UPSERT = """
            INSERT INTO scheduled_job_status (task_key, last_started_at, last_finished_at, last_result,
                                              last_duration_ms, consecutive_failures, last_failure_at,
                                              last_failure_message, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (task_key) DO UPDATE SET
                last_started_at = EXCLUDED.last_started_at,
                last_finished_at = EXCLUDED.last_finished_at,
                last_result = EXCLUDED.last_result,
                last_duration_ms = EXCLUDED.last_duration_ms,
                consecutive_failures = CASE WHEN EXCLUDED.last_result = 'FAILURE'
                    THEN scheduled_job_status.consecutive_failures + 1 ELSE 0 END,
                last_failure_at = COALESCE(EXCLUDED.last_failure_at, scheduled_job_status.last_failure_at),
                last_failure_message = COALESCE(EXCLUDED.last_failure_message,
                    scheduled_job_status.last_failure_message),
                updated_at = EXCLUDED.updated_at
            """;

    private static final String SELECT_ALL = """
            SELECT task_key, last_started_at, last_finished_at, last_result, last_duration_ms,
                   consecutive_failures, last_failure_at, last_failure_message
            FROM scheduled_job_status
            ORDER BY consecutive_failures DESC, last_finished_at DESC, task_key
            """;

    private final JdbcTemplate jdbc;
    private final RequiresNewTransactionExecutor transactions;

    /** 记录一次执行结果;失败时消息按 512 截断,成功时计数归零但保留最近一次失败信息。 */
    public void record(JobExecutionReport report) {
        try {
            transactions.execute(() -> {
                jdbc.update(UPSERT,
                        report.task(),
                        report.startedAt().atOffset(ZoneOffset.UTC),
                        report.finishedAt().atOffset(ZoneOffset.UTC),
                        report.success() ? RESULT_SUCCESS : RESULT_FAILURE,
                        report.durationMillis(),
                        report.success() ? 0 : 1,
                        report.success() ? null : report.finishedAt().atOffset(ZoneOffset.UTC),
                        report.success() ? null : truncate(report.failureMessage()),
                        report.finishedAt().atOffset(ZoneOffset.UTC));
                return null;
            });
        } catch (RuntimeException exception) {
            log.warn("记录定时任务状态失败 task={}", report.task(), exception);
        }
    }

    /** 返回全部任务的最近一次执行状态;连续失败多的排在前。 */
    public List<JobExecutionStatus> findAll() {
        return jdbc.query(SELECT_ALL, JobExecutionStatusStore::mapRow);
    }

    private static JobExecutionStatus mapRow(ResultSet results, int rowNumber) throws SQLException {
        return new JobExecutionStatus(
                results.getString("task_key"),
                toInstant(results.getObject("last_started_at", OffsetDateTime.class)),
                toInstant(results.getObject("last_finished_at", OffsetDateTime.class)),
                results.getString("last_result"),
                results.getLong("last_duration_ms"),
                results.getInt("consecutive_failures"),
                toInstant(results.getObject("last_failure_at", OffsetDateTime.class)),
                results.getString("last_failure_message"));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= FAILURE_MESSAGE_MAX_LENGTH
                ? message
                : message.substring(0, FAILURE_MESSAGE_MAX_LENGTH);
    }
}