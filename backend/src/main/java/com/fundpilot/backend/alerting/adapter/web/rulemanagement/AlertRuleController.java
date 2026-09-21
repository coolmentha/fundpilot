package com.fundpilot.backend.alerting.adapter.web.rulemanagement;

import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "价格提醒规则接口", description = "提醒规则的增删改查与启停")
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleCommandHandler commands;
    private final AlertRuleQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询提醒规则列表")
    public ApiResponse<List<AlertRuleView>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.findByOwner(ownerId).stream().map(AlertRuleView::from).toList());
    }

    @PostMapping
    @Operation(summary = "新建提醒规则")
    public ApiResponse<AlertRuleView> create(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                             @RequestBody Request request) {
        return ApiResponse.ok(AlertRuleView.from(commands.create(ownerId, request.toInput())));
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "编辑提醒规则")
    public ApiResponse<AlertRuleView> update(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                             @PathVariable long ruleId, @RequestBody Request request) {
        return ApiResponse.ok(AlertRuleView.from(commands.update(ownerId, ruleId, request.toInput())));
    }

    @PostMapping("/{ruleId}/{action:enable|disable}")
    @Operation(summary = "启用或禁用提醒规则")
    public ApiResponse<AlertRuleView> changeEnabled(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                    @PathVariable long ruleId, @PathVariable String action) {
        boolean enabled = switch (action) {
            case "enable" -> true;
            case "disable" -> false;
            default -> throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "不支持的规则操作: " + action);
        };
        return ApiResponse.ok(AlertRuleView.from(commands.changeEnabled(ownerId, ruleId, enabled)));
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "删除提醒规则")
    public ApiResponse<Void> delete(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                    @PathVariable long ruleId) {
        commands.delete(ownerId, ruleId);
        return ApiResponse.ok(null);
    }

    @Schema(description = "提醒规则创建/更新请求")
    public record Request(
            @Schema(description = "作用范围，枚举（GLOBAL 全部关注基金 / FUND 指定基金）", example = "GLOBAL") String scope,
            @Schema(description = "指定基金 ID，scope=FUND 时必填、GLOBAL 时须为空", example = "12") Long portfolioFundId,
            @Schema(description = "提醒类型，枚举（RISE 上涨 / DROP 下跌 / PROFIT 盈利）", example = "RISE") String ruleType,
            @Schema(description = "阈值，小数表示（0.05 即 5%）", example = "0.05") BigDecimal threshold,
            @Schema(description = "是否启用，缺省为 true", example = "true") Boolean enabled) {

        AlertRuleCommandHandler.RuleInput toInput() {
            return new AlertRuleCommandHandler.RuleInput(scope, portfolioFundId, ruleType, threshold, enabled);
        }
    }

    @Schema(description = "提醒规则视图")
    public record AlertRuleView(
            @Schema(description = "规则 ID", example = "1") long id,
            @Schema(description = "作用范围", example = "FUND") String scope,
            @Schema(description = "指定基金 ID，全局规则为空", example = "12") Long portfolioFundId,
            @Schema(description = "基金代码，全局规则为空", example = "161725") String fundCode,
            @Schema(description = "基金名称，全局规则为空", example = "招商中证白酒指数") String fundName,
            @Schema(description = "提醒类型", example = "RISE") String ruleType,
            @Schema(description = "阈值，小数表示", example = "0.05") BigDecimal threshold,
            @Schema(description = "是否启用", example = "true") boolean enabled,
            @Schema(description = "今日是否已提醒", example = "false") boolean todaySent,
            @Schema(description = "上次触发时间") Instant lastTriggeredAt) {

        static AlertRuleView from(AlertRuleQueryHandler.RuleViewResult result) {
            return new AlertRuleView(result.id(), result.scope(), result.portfolioFundId(), result.fundCode(),
                    result.fundName(), result.ruleType(), result.threshold(), result.enabled(), result.todaySent(),
                    result.lastTriggeredAt());
        }
    }
}
