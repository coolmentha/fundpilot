package com.fundpilot.backend.investmentplan.application.query.budgetmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanForecastQueryHandler;
import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudgetRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 月度计划现金流预测；已撤销流水占用计划日但不计已投入金额。 */
@Service
@RequiredArgsConstructor
public class InvestmentPlanBudgetSummaryQueryHandler {
    private final InvestmentPlanBudgetRepository budgets;
    private final InvestmentPlanRepository plans;
    private final PlanTransactionGateway transactions;
    private final InvestmentPlanForecastQueryHandler forecasts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Summary currentMonth(long ownerId) {
        var now = clock.instant();
        var monthStart = InvestmentPlanForecastQueryHandler.monthStart(now);
        var monthEnd = InvestmentPlanForecastQueryHandler.nextMonthStart(now);
        BigDecimal invested = transactions.investedAmount(ownerId, monthStart, monthEnd);
        var activePlans = plans.findByOwnerId(ownerId);
        var datesByPlan = forecasts.currentMonthExecutionDates(ownerId, activePlans);
        BigDecimal future = activePlans.stream().map(plan -> plan.amount().multiply(BigDecimal.valueOf(
                datesByPlan.getOrDefault(plan.id(), java.util.List.of()).size())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal projected = invested.add(future);
        BigDecimal budget = budgets.findByOwnerId(ownerId).map(value -> value.monthlyBudget()).orElse(null);
        BigDecimal remaining = null;
        BigDecimal over = null;
        if (budget != null) {
            BigDecimal difference = budget.subtract(projected);
            remaining = difference.max(BigDecimal.ZERO);
            over = difference.signum() < 0 ? difference.negate() : BigDecimal.ZERO;
        }
        return new Summary(budget, invested, future, projected, remaining, over);
    }

    public record Summary(BigDecimal monthlyBudget, BigDecimal investedAmount, BigDecimal futureAmount,
                          BigDecimal projectedAmount, BigDecimal remainingAmount, BigDecimal overBudgetAmount) {}
}
