package com.fundpilot.backend.alerting.adapter.web.notificationhistory;

import com.fundpilot.backend.alerting.application.query.notificationhistory.AlertNotificationHistoryQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "价格提醒记录接口", description = "提醒发送历史查询")
@RestController
@RequestMapping("/api/alert-notifications")
@RequiredArgsConstructor
public class AlertNotificationController {

    private final AlertNotificationHistoryQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询最近的提醒记录")
    public ApiResponse<List<AlertNotificationView>> list(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(queries.findLatest(ownerId, limit).stream().map(AlertNotificationView::from).toList());
    }

    @Schema(description = "提醒记录视图")
    public record AlertNotificationView(
            @Schema(description = "记录 ID", example = "1") long id,
            @Schema(description = "规则 ID", example = "1") long alertRuleId,
            @Schema(description = "提醒类型", example = "RISE") String ruleType,
            @Schema(description = "阈值，小数表示", example = "0.05") BigDecimal threshold,
            @Schema(description = "触发摘要", example = "招商中证白酒指数(161725) 上涨5.32%") String triggerSummary,
            @Schema(description = "命中基金数", example = "1") int fundCount,
            @Schema(description = "发送状态，枚举（SENT 已发送 / FAILED 失败）", example = "SENT") String status,
            @Schema(description = "失败原因，成功时为空") String failureReason,
            @Schema(description = "交易日") Instant tradingDate,
            @Schema(description = "发送时间，失败时为空") Instant sentAt,
            @Schema(description = "接收邮箱") String recipientEmail) {

        static AlertNotificationView from(AlertNotificationHistoryQueryHandler.NotificationViewResult result) {
            return new AlertNotificationView(result.id(), result.alertRuleId(), result.ruleType(), result.threshold(),
                    result.triggerSummary(), result.fundCount(), result.status(), result.failureReason(),
                    result.tradingDate(), result.sentAt(), result.recipientEmail());
        }
    }
}
