package com.fundpilot.backend.platform.observability;

import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class JobExecutionStatusStoreTest extends AbstractIntegrationTest {

    private static final String TASK = "SampleJob.runRefresh";
    private static final Instant START = Instant.parse("2026-07-27T00:00:00Z");
    private static final Instant FINISH = Instant.parse("2026-07-27T00:00:03Z");

    @Autowired
    JobExecutionStatusStore statuses;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM scheduled_job_status WHERE task_key = ?", TASK);
    }

    @Test
    void 成功归零连续失败但仍保留最近一次失败信息() {
        statuses.record(report(true, null));
        assertThat(task()).satisfies(row -> {
            assertThat(row.lastResult()).isEqualTo("SUCCESS");
            assertThat(row.consecutiveFailures()).isZero();
            assertThat(row.lastFailureMessage()).isNull();
            assertThat(row.lastDurationMillis()).isEqualTo(3000L);
            assertThat(row.lastFinishedAt()).isEqualTo(FINISH);
        });

        statuses.record(report(false, "boom"));
        assertThat(task()).satisfies(row -> {
            assertThat(row.lastResult()).isEqualTo("FAILURE");
            assertThat(row.consecutiveFailures()).isEqualTo(1);
            assertThat(row.lastFailureMessage()).isEqualTo("boom");
            assertThat(row.lastFailureAt()).isEqualTo(FINISH);
        });

        statuses.record(report(false, "boom2"));
        assertThat(task()).satisfies(row -> {
            assertThat(row.consecutiveFailures()).isEqualTo(2);
            assertThat(row.lastFailureMessage()).isEqualTo("boom2");
        });

        statuses.record(report(true, null));
        assertThat(task()).satisfies(row -> {
            assertThat(row.lastResult()).isEqualTo("SUCCESS");
            assertThat(row.consecutiveFailures()).isZero();
            assertThat(row.lastFailureAt()).isEqualTo(FINISH);
            assertThat(row.lastFailureMessage()).isEqualTo("boom2");
        });
    }

    @Test
    void 失败消息超长时按五百一十二字符截断() {
        statuses.record(report(false, "x".repeat(600)));

        assertThat(task().lastFailureMessage()).hasSize(512);
    }

    @Test
    void 写入自身出错只记日志不抛出() {
        // task_key 超 VARCHAR(200) 必然写失败;切面 finally 中抛出会顶掉原始任务异常,故必须吞掉
        JobExecutionReport oversized =
                new JobExecutionReport("x".repeat(300), START, FINISH, 3000L, false, "boom");

        statuses.record(oversized);

        assertThat(findTasks()).noneMatch(row -> row.task().startsWith("xxx"));
    }

    private JobExecutionReport report(boolean success, String failureMessage) {
        return new JobExecutionReport(TASK, START, FINISH, 3000L, success, failureMessage);
    }

    private JobExecutionStatus task() {
        return findTasks().stream().filter(row -> row.task().equals(TASK)).findFirst().orElseThrow();
    }

    private List<JobExecutionStatus> findTasks() {
        return statuses.findAll();
    }
}