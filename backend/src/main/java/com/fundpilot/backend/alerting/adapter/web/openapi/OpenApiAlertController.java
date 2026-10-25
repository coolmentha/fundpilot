package com.fundpilot.backend.alerting.adapter.web.openapi;

import com.fundpilot.backend.alerting.adapter.web.notificationhistory.AlertNotificationController;
import com.fundpilot.backend.alerting.adapter.web.rulemanagement.AlertRuleController;
import com.fundpilot.backend.alerting.application.query.notificationhistory.AlertNotificationHistoryQueryHandler;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Open API v1 提醒规则与发送记录只读资源。 */
@Tag(name = "Open API 提醒", description = "当前用户的提醒规则与发送历史")
@RestController
@RequestMapping("/api/open/v1/alerts")
@RequiredArgsConstructor
public class OpenApiAlertController {

    private final AlertRuleQueryHandler rules;
    private final AlertNotificationHistoryQueryHandler notifications;

    @GetMapping("/rules")
    @Operation(summary = "查询提醒规则")
    public ApiResponse<List<RuleView>> rules(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(rules.findByOwner(ownerId).stream().map(RuleView::from).toList());
    }

    @GetMapping("/notifications")
    @Operation(summary = "查询最近提醒记录")
    public ApiResponse<List<NotificationView>> notifications(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(notifications.findLatest(ownerId, limit).stream().map(NotificationView::from).toList());
    }

    public record RuleView(long id, String scope, Long portfolioFundId, String fundCode, String fundName,
                           String kind, String kindLabel, String match, String conditionSummary,
                           List<ConditionView> conditions, TakeProfitView takeProfit, GridView grid,
                           boolean enabled, boolean todaySent, java.time.Instant lastTriggeredAt) {
        static RuleView from(AlertRuleQueryHandler.RuleViewResult value) {
            return new RuleView(value.id(), value.scope(), value.portfolioFundId(), value.fundCode(), value.fundName(),
                    value.kind(), value.kindLabel(), value.match(), value.conditionSummary(),
                    value.conditions().stream().map(ConditionView::from).toList(),
                    TakeProfitView.from(value.takeProfit()), GridView.from(value.grid()), value.enabled(),
                    value.todaySent(), value.lastTriggeredAt());
        }
    }
    public record ConditionView(String indicator, java.util.Map<String, Integer> params, String relation,
                                java.math.BigDecimal value) {
        static ConditionView from(AlertRuleQueryHandler.ConditionView value) {
            return new ConditionView(value.indicator(), value.params(), value.relation(), value.value());
        }
    }
    public record TakeProfitView(java.math.BigDecimal activation, java.math.BigDecimal pullback,
                                 java.math.BigDecimal harvest, java.math.BigDecimal minimumHolding,
                                 java.math.BigDecimal maxSingleSell, int cooldownDays) {
        static TakeProfitView from(AlertRuleQueryHandler.TakeProfitView value) {
            return value == null ? null : new TakeProfitView(value.activation(), value.pullback(), value.harvest(),
                    value.minimumHolding(), value.maxSingleSell(), value.cooldownDays());
        }
    }
    public record GridView(java.math.BigDecimal upRatio, java.math.BigDecimal downRatio) {
        static GridView from(AlertRuleQueryHandler.GridView value) {
            return value == null ? null : new GridView(value.upRatio(), value.downRatio());
        }
    }
    public record NotificationView(long id, long alertRuleId, String triggerSummary, int fundCount,
                                   String status, String failureReason, java.time.Instant tradingDate,
                                   java.time.Instant sentAt) {
        static NotificationView from(AlertNotificationHistoryQueryHandler.NotificationViewResult value) {
            return new NotificationView(value.id(), value.alertRuleId(), value.triggerSummary(), value.fundCount(),
                    value.status(), value.failureReason(), value.tradingDate(), value.sentAt());
        }
    }
}
