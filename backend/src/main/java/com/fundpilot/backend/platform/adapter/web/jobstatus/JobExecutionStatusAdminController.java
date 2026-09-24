package com.fundpilot.backend.platform.adapter.web.jobstatus;

import com.fundpilot.backend.platform.observability.JobExecutionStatus;
import com.fundpilot.backend.platform.observability.JobExecutionStatusStore;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理 - 定时任务状态接口", description = "定时任务最近一次执行状态")
@RestController
@RequestMapping("/api/admin/jobs")
@RequiredArgsConstructor
public class JobExecutionStatusAdminController {

    private final JobExecutionStatusStore statuses;

    @GetMapping
    @Operation(summary = "查询定时任务最近执行状态")
    public ApiResponse<List<JobExecutionView>> list() {
        return ApiResponse.ok(statuses.findAll().stream().map(JobExecutionView::from).toList());
    }

    public record JobExecutionView(String task, Instant lastStartedAt, Instant lastFinishedAt,
                                   String lastResult, long lastDurationMillis, int consecutiveFailures,
                                   Instant lastFailureAt, String lastFailureMessage) {

        static JobExecutionView from(JobExecutionStatus status) {
            return new JobExecutionView(status.task(), status.lastStartedAt(), status.lastFinishedAt(),
                    status.lastResult(), status.lastDurationMillis(), status.consecutiveFailures(),
                    status.lastFailureAt(), status.lastFailureMessage());
        }
    }
}