package com.fundpilot.backend.investmentplan.adapter.web.openapi;

import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Open API v1 定投计划只读资源。 */
@Tag(name = "Open API 定投", description = "当前用户的定投计划")
@RestController
@RequestMapping("/api/open/v1/investment-plans")
@RequiredArgsConstructor
public class OpenApiInvestmentPlanController {

    private final InvestmentPlanQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询定投计划列表")
    public ApiResponse<List<PlanView>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.list(ownerId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/{portfolioFundId}")
    @Operation(summary = "按组合基金查询定投计划")
    public ApiResponse<List<PlanView>> byFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                              @PathVariable long portfolioFundId) {
        return ApiResponse.ok(queries.listByPortfolioFund(ownerId, portfolioFundId).stream()
                .map(PlanView::from).toList());
    }

    public record PlanView(Long id, long portfolioFundId, boolean enabled, BigDecimal amount,
                           String frequency, Integer dayOfWeek, Integer dayOfMonth, String status,
                           String amountStrategy, String referenceIndexCode, Integer movingAverageDays,
                           BigDecimal minimumAmount, BigDecimal maximumAmount, Instant createdDate,
                           int remainingOccurrences, BigDecimal remainingAmount,
                           List<Instant> remainingExecutionDates, LatestDecision latestDecision) {
        static PlanView from(InvestmentPlanCommandHandler.PlanResult value) {
            var dates = value.remainingExecutionDates();
            return new PlanView(value.id(), value.portfolioFundId(), value.enabled(), value.amount(),
                    value.frequency(), value.dayOfWeek(), value.dayOfMonth(), value.status(),
                    value.amountStrategy(), value.referenceIndexCode(), value.movingAverageDays(),
                    value.minimumAmount(), value.maximumAmount(), value.createdDate(), dates.size(),
                    value.amount().multiply(BigDecimal.valueOf(dates.size())), dates,
                    LatestDecision.from(value.latestDecision()));
        }
    }

    public record LatestDecision(String result, BigDecimal actualAmount, BigDecimal deductionRate,
                                 String ruleVersion, Instant dataDate, String reasonCode, String reason) {
        static LatestDecision from(InvestmentPlanCommandHandler.LatestDecision value) {
            return value == null ? null : new LatestDecision(value.result(), value.actualAmount(),
                    value.deductionRate(), value.ruleVersion(), value.dataDate(), value.reasonCode(), value.reason());
        }
    }
}
