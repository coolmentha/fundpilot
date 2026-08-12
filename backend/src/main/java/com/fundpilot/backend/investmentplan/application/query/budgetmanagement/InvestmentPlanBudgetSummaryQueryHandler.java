package com.fundpilot.backend.investmentplan.application.query.budgetmanagement;

import com.fundpilot.backend.investmentplan.application.gateway.planexecution.PlanTransactionGateway;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanForecastQueryHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanVisibleQueryHandler;
import com.fundpilot.backend.investmentplan.domain.budget.InvestmentPlanBudgetRepository;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
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
    private final PlanTransactionGateway transactions;
    private final InvestmentPlanForecastQueryHandler forecasts;
    private final InvestmentPlanVisibleQueryHandler visiblePlans;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Summary currentMonth(long ownerId) {
        var now = clock.instant();
        var monthStart = InvestmentPlanForecastQueryHandler.monthStart(now);
        var monthEnd = InvestmentPlanForecastQueryHandler.nextMonthStart(now);
        BigDecimal invested = transactions.investedAmount(ownerId, monthStart, monthEnd);
        var activePlans = visiblePlans.findByOwner(ownerId);
        var datesByPlan = forecasts.currentMonthExecutionDates(ownerId, activePlans);
        BigDecimal future = activePlans.stream().map(plan -> plan.amount().multiply(BigDecimal.valueOf(
                datesByPlan.getOrDefault(plan.id(), java.util.List.of()).size())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean hasSmartPlan = activePlans.stream().anyMatch(plan -> plan.amountStrategy()
                != com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanAmountStrategy.FIXED);
        BigDecimal minimumFuture = hasSmartPlan ? activePlans.stream().map(plan -> plan.amount()
                .multiply(minimumRate(plan)).multiply(BigDecimal.valueOf(
                        datesByPlan.getOrDefault(plan.id(), java.util.List.of()).size())))
                .reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal maximumFuture = hasSmartPlan ? activePlans.stream().map(plan -> plan.amount()
                .multiply(maximumRate(plan)).multiply(BigDecimal.valueOf(
                        datesByPlan.getOrDefault(plan.id(), java.util.List.of()).size())))
                .reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal projected = invested.add(future);
        BigDecimal minimumProjected = minimumFuture == null ? null : invested.add(minimumFuture);
        BigDecimal maximumProjected = maximumFuture == null ? null : invested.add(maximumFuture);
        BigDecimal budget = budgets.findByOwnerId(ownerId).map(value -> value.monthlyBudget()).orElse(null);
        BigDecimal remaining = null;
        BigDecimal over = null;
        if (budget != null) {
            BigDecimal difference = budget.subtract(projected);
            remaining = difference.max(BigDecimal.ZERO);
            over = difference.signum() < 0 ? difference.negate() : BigDecimal.ZERO;
        }
        return new Summary(budget, invested, future, projected, remaining, over,
                minimumFuture, maximumFuture, minimumProjected, maximumProjected);
    }

    private static BigDecimal minimumRate(InvestmentPlan plan) {
        return switch (plan.amountStrategy()) {
            case LOW_VALUATION -> BigDecimal.ZERO;
            case MOVING_AVERAGE -> new BigDecimal("0.60");
            case CHANGE_RATE -> new BigDecimal("0.50");
            case FIXED -> BigDecimal.ONE;
        };
    }

    private static BigDecimal maximumRate(InvestmentPlan plan) {
        return switch (plan.amountStrategy()) {
            case LOW_VALUATION, FIXED -> BigDecimal.ONE;
            case MOVING_AVERAGE -> new BigDecimal("2.10");
            case CHANGE_RATE -> new BigDecimal("2.00");
        };
    }

    public record Summary(BigDecimal monthlyBudget, BigDecimal investedAmount, BigDecimal futureAmount,
                          BigDecimal projectedAmount, BigDecimal remainingAmount, BigDecimal overBudgetAmount,
                          BigDecimal minimumFutureAmount, BigDecimal maximumFutureAmount,
                          BigDecimal minimumProjectedAmount, BigDecimal maximumProjectedAmount) {}
}
