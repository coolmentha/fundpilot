package com.fundpilot.backend.investmentplan.application.query.planexecution;

import com.fundpilot.backend.investmentplan.application.gateway.planmanagement.PlanPortfolioFundGateway;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlan;
import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 只返回关联组合基金仍可见的定投计划，供列表和预算摘要共享。 */
@Service
@RequiredArgsConstructor
public class InvestmentPlanVisibleQueryHandler {
    private final InvestmentPlanRepository plans;
    private final PlanPortfolioFundGateway portfolioFunds;

    @Transactional(readOnly = true)
    public List<InvestmentPlan> findByOwner(long ownerId) {
        Set<Long> trackedPortfolioFundIds = portfolioFunds.findTrackedByOwner(ownerId).stream()
                .map(PlanPortfolioFundGateway.PortfolioFund::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return plans.findByOwnerId(ownerId).stream()
                .filter(plan -> trackedPortfolioFundIds.contains(plan.portfolioFundId()))
                .toList();
    }
}
