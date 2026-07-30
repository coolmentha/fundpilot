package com.fundpilot.backend.investmentplan.application.query.planexecution;

import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler;
import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InvestmentPlanQueryHandler {
    private final InvestmentPlanRepository plans;
    private final PlanPortfolioFundGateway portfolioFunds;
    private final InvestmentPlanForecastQueryHandler forecasts;
    @Transactional(readOnly = true)
    public List<Long> effectiveEnabledIds() { return plans.findEffectiveEnabled().stream().map(plan -> plan.id()).toList(); }

    @Transactional(readOnly = true)
    public List<InvestmentPlanCommandHandler.PlanResult> list(long ownerId) {
        var ownedPlans = plans.findByOwnerId(ownerId);
        var executionDates = forecasts.currentMonthExecutionDates(ownerId, ownedPlans);
        return ownedPlans.stream().map(plan -> InvestmentPlanCommandHandler.from(plan)
                .withForecast(executionDates.getOrDefault(plan.id(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public List<InvestmentPlanCommandHandler.PlanResult> listByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return listByPortfolioFund(ownerId, fund.id());
    }

    @Transactional(readOnly = true)
    public List<InvestmentPlanCommandHandler.PlanResult> listByPortfolioFund(long ownerId, long portfolioFundId) {
        portfolioFunds.requireTracked(ownerId, portfolioFundId);
        return plans.findByPortfolioFundId(portfolioFundId).stream().map(InvestmentPlanCommandHandler::from).toList();
    }

    @Transactional(readOnly = true)
    public InvestmentPlanCommandHandler.PlanResult activeByLegacyFund(long ownerId, long legacyFundId) {
        var fund = portfolioFunds.requireTrackedByLegacyFund(ownerId, legacyFundId);
        return activeByPortfolioFund(ownerId, fund.id());
    }

    @Transactional(readOnly = true)
    public InvestmentPlanCommandHandler.PlanResult activeByPortfolioFund(long ownerId, long portfolioFundId) {
        portfolioFunds.requireTracked(ownerId, portfolioFundId);
        return plans.findEffectiveByPortfolioFundId(portfolioFundId).map(InvestmentPlanCommandHandler::from).orElse(null);
    }
}
