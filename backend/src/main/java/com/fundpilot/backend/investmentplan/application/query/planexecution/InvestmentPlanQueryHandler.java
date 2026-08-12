package com.fundpilot.backend.investmentplan.application.query.planexecution;

import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecutionRepository;
import com.fundpilot.backend.investmentplan.domain.execution.InvestmentPlanExecution;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentPlanQueryHandler {
    private final InvestmentPlanRepository plans;
    private final PlanPortfolioFundGateway portfolioFunds;
    private final InvestmentPlanForecastQueryHandler forecasts;
    private final InvestmentPlanVisibleQueryHandler visiblePlans;
    private final InvestmentPlanExecutionRepository executions;

    @Transactional(readOnly = true)
    public List<Long> effectiveEnabledIds() { return plans.findEffectiveEnabled().stream().map(plan -> plan.id()).toList(); }

    @Transactional(readOnly = true)
    public List<InvestmentPlanCommandHandler.PlanResult> list(long ownerId) {
        var ownedPlans = visiblePlans.findByOwner(ownerId);
        var executionDates = forecasts.currentMonthExecutionDates(ownerId, ownedPlans);
        Map<Long, InvestmentPlanExecution> latest = latest(ownedPlans);
        return ownedPlans.stream().map(plan -> InvestmentPlanCommandHandler.from(plan)
                .withForecast(executionDates.getOrDefault(plan.id(), List.of()))
                .withLatestDecision(toLatest(latest.get(plan.id())))).toList();
    }

    @Transactional(readOnly = true)
    public List<InvestmentPlanCommandHandler.PlanResult> listByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return listByPortfolioFund(ownerId, fund.id());
    }

    @Transactional(readOnly = true)
    public List<InvestmentPlanCommandHandler.PlanResult> listByPortfolioFund(long ownerId, long portfolioFundId) {
        portfolioFunds.requireTracked(ownerId, portfolioFundId);
        var values = plans.findByPortfolioFundId(portfolioFundId);
        Map<Long, InvestmentPlanExecution> latest = latest(values);
        return values.stream().map(plan -> InvestmentPlanCommandHandler.from(plan)
                .withLatestDecision(toLatest(latest.get(plan.id())))).toList();
    }

    @Transactional(readOnly = true)
    public InvestmentPlanCommandHandler.PlanResult activeByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return activeByPortfolioFund(ownerId, fund.id());
    }

    @Transactional(readOnly = true)
    public InvestmentPlanCommandHandler.PlanResult activeByPortfolioFund(long ownerId, long portfolioFundId) {
        portfolioFunds.requireTracked(ownerId, portfolioFundId);
        return plans.findEffectiveByPortfolioFundId(portfolioFundId).map(plan ->
                InvestmentPlanCommandHandler.from(plan).withLatestDecision(toLatest(latest(List.of(plan)).get(plan.id()))))
                .orElse(null);
    }

    private Map<Long, InvestmentPlanExecution> latest(List<InvestmentPlan> values) {
        if (values.isEmpty()) return Map.of();
        return executions.findLatestByPlanIds(values.stream().map(InvestmentPlan::id).toList()).stream()
                .collect(Collectors.toMap(InvestmentPlanExecution::planId, Function.identity()));
    }

    private static InvestmentPlanCommandHandler.LatestDecision toLatest(InvestmentPlanExecution value) {
        return value == null ? null : new InvestmentPlanCommandHandler.LatestDecision(value.result().name(),
                value.actualAmount(), value.deductionRate(), value.ruleVersion(), value.dataDate(),
                value.reasonCode(), value.reason());
    }
}
