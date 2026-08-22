package com.fundpilot.backend.investmentplan.adapter.web.planmanagement;

import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
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

@Tag(name = "定投计划接口", description = "定投计划相关操作")
@RestController
@RequestMapping("/api/investment-plans")
@RequiredArgsConstructor
public class InvestmentPlanController {
    private final InvestmentPlanCommandHandler commands;
    private final InvestmentPlanQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询定投计划列表")
    public ApiResponse<List<PlanView>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.list(ownerId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}")
    @Operation(summary = "按基金查询定投计划列表")
    public ApiResponse<List<PlanView>> listByFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                               @PathVariable long legacyFundId) {
        return ApiResponse.ok(queries.listByLegacyFund(ownerId, legacyFundId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}")
    @Operation(summary = "按组合基金查询定投计划列表")
    public ApiResponse<List<PlanView>> listByPortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                        @PathVariable long portfolioFundId) {
        return ApiResponse.ok(queries.listByPortfolioFund(ownerId, portfolioFundId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}/active")
    @Operation(summary = "查询基金生效中的定投计划")
    public ApiResponse<PlanView> active(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long legacyFundId) {
        var plan = queries.activeByLegacyFund(ownerId, legacyFundId);
        return ApiResponse.ok(plan == null ? null : PlanView.from(plan));
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}/active")
    @Operation(summary = "查询组合基金生效中的定投计划")
    public ApiResponse<PlanView> activePortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                   @PathVariable long portfolioFundId) {
        var plan = queries.activeByPortfolioFund(ownerId, portfolioFundId);
        return ApiResponse.ok(plan == null ? null : PlanView.from(plan));
    }

    @PostMapping("/funds/{legacyFundId}")
    @Operation(summary = "创建定投计划")
    public ApiResponse<PlanView> create(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long legacyFundId, @RequestBody Request request) {
        return ApiResponse.ok(PlanView.from(commands.create(ownerId, legacyFundId, request.toInput())));
    }

    @PostMapping("/portfolio-funds/{portfolioFundId}")
    @Operation(summary = "为组合基金创建定投计划")
    public ApiResponse<PlanView> createPortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                   @PathVariable long portfolioFundId, @RequestBody Request request) {
        return ApiResponse.ok(PlanView.from(commands.createForPortfolioFund(ownerId, portfolioFundId, request.toInput())));
    }

    @PutMapping("/{planId}")
    @Operation(summary = "更新定投计划")
    public ApiResponse<PlanView> update(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long planId, @RequestBody Request request) {
        return ApiResponse.ok(PlanView.from(commands.update(ownerId, planId, request.toInput())));
    }

    @PostMapping("/{planId}/{action:activate|retire|pause|resume}")
    @Operation(summary = "执行定投计划操作（激活/退役/暂停/恢复）")
    public ApiResponse<PlanView> action(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long planId, @PathVariable String action) {
        var plan = switch (action) {
            case "activate" -> commands.activate(ownerId, planId);
            case "retire" -> commands.retire(ownerId, planId);
            case "pause" -> commands.setEnabled(ownerId, planId, false);
            case "resume" -> commands.setEnabled(ownerId, planId, true);
            default -> throw new BusinessException(ErrorCode.PLAN_ACTION_INVALID, "不支持的计划操作: " + action);
        };
        return ApiResponse.ok(PlanView.from(plan));
    }

    @DeleteMapping("/{planId}")
    @Operation(summary = "删除定投计划")
    public ApiResponse<Void> delete(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                 @PathVariable long planId) {
        commands.delete(ownerId, planId);
        return ApiResponse.ok(null);
    }

    @Schema(description = "定投计划创建/更新请求")
    public record Request(
            @Schema(description = "是否启用，true 启用 / false 暂停扣款", example = "true") boolean enabled,
            @Schema(description = "每期定投金额", example = "500.00") BigDecimal amount,
            @Schema(description = "扣款频率，枚举（DAILY 每日 / WEEKLY 每周 / MONTHLY 每月）", example = "MONTHLY") String frequency,
            @Schema(description = "每周几扣款，WEEKLY 频率时使用", example = "1") Integer dayOfWeek,
            @Schema(description = "每月几号扣款，MONTHLY 频率时使用", example = "1") Integer dayOfMonth,
            @Schema(description = "金额策略，枚举（FIXED 固定金额 / LOW_VALUATION 低估值 / MOVING_AVERAGE 移动平均），默认 FIXED", example = "FIXED") String amountStrategy,
            @Schema(description = "参考指数代码，LOW_VALUATION/MOVING_AVERAGE 策略时使用", example = "000300.SH") String referenceIndexCode,
            @Schema(description = "移动平均天数，MOVING_AVERAGE 策略时使用", example = "20") Integer movingAverageDays) {
        public Request(boolean enabled, BigDecimal amount, String frequency,
                       Integer dayOfWeek, Integer dayOfMonth) {
            this(enabled, amount, frequency, dayOfWeek, dayOfMonth, "FIXED", null, null);
        }
        InvestmentPlanCommandHandler.PlanInput toInput() {
            return new InvestmentPlanCommandHandler.PlanInput(enabled, amount, frequency, dayOfWeek, dayOfMonth,
                    amountStrategy, referenceIndexCode, movingAverageDays);
        }
    }
    @Schema(description = "定投计划视图")
    public record PlanView(Long id, long portfolioFundId, boolean enabled, BigDecimal amount,
                           String frequency, Integer dayOfWeek, Integer dayOfMonth,
                           String status, String amountStrategy, String referenceIndexCode,
                           Integer movingAverageDays, BigDecimal minimumAmount, BigDecimal maximumAmount,
                           Instant createdDate, int remainingOccurrences, BigDecimal remainingAmount,
                           List<Instant> remainingExecutionDates,
                           InvestmentPlanCommandHandler.LatestDecision latestDecision) {
        static PlanView from(InvestmentPlanCommandHandler.PlanResult result) {
            var dates = result.remainingExecutionDates();
            return new PlanView(result.id(), result.portfolioFundId(), result.enabled(), result.amount(),
                    result.frequency(), result.dayOfWeek(), result.dayOfMonth(), result.status(),
                    result.amountStrategy(), result.referenceIndexCode(), result.movingAverageDays(),
                    result.minimumAmount(), result.maximumAmount(), result.createdDate(), dates.size(),
                    result.amount().multiply(BigDecimal.valueOf(dates.size())), dates, result.latestDecision());
        }
    }
    @Schema(description = "统一响应结果")
    record ApiResponse<T>(boolean success, T data, String code, String message) {
        static <T> ApiResponse<T> ok(T data) { return new ApiResponse<>(true, data, null, null); }
    }
}
