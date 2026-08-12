package com.fundpilot.backend.investmentplan.adapter.web.budgetmanagement;

import com.fundpilot.backend.investmentplan.application.command.budgetmanagement.InvestmentPlanBudgetCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.budgetmanagement.InvestmentPlanBudgetQueryHandler;
import com.fundpilot.backend.investmentplan.application.query.budgetmanagement.InvestmentPlanBudgetSummaryQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investment-plan-budget")
@RequiredArgsConstructor
public class InvestmentPlanBudgetController {
    private final InvestmentPlanBudgetCommandHandler commands;
    private final InvestmentPlanBudgetQueryHandler queries;
    private final InvestmentPlanBudgetSummaryQueryHandler summaries;
    @GetMapping public Response<View> get(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return Response.ok(new View(queries.get(ownerId)));
    }
    @PutMapping public Response<View> update(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                             @RequestBody Request request) {
        return Response.ok(new View(commands.set(ownerId, request.monthlyBudget())));
    }
    @GetMapping("/summary") public Response<SummaryView> summary(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        var summary = summaries.currentMonth(ownerId);
        return Response.ok(new SummaryView(summary.monthlyBudget(), summary.investedAmount(), summary.futureAmount(),
                summary.projectedAmount(), summary.remainingAmount(), summary.overBudgetAmount(),
                summary.minimumFutureAmount(), summary.maximumFutureAmount(), summary.minimumProjectedAmount(),
                summary.maximumProjectedAmount()));
    }
    public record Request(BigDecimal monthlyBudget) {}
    public record View(BigDecimal monthlyBudget) {}
    public record SummaryView(BigDecimal monthlyBudget, BigDecimal investedAmount, BigDecimal futureAmount,
                              BigDecimal projectedAmount, BigDecimal remainingAmount, BigDecimal overBudgetAmount,
                              BigDecimal minimumFutureAmount, BigDecimal maximumFutureAmount,
                              BigDecimal minimumProjectedAmount, BigDecimal maximumProjectedAmount) {}
    record Response<T>(boolean success, T data, String code, String message) {
        static <T> Response<T> ok(T data) { return new Response<>(true, data, null, null); }
    }
}
