package com.fundpilot.backend.investmentplan.application.command.planlifecycle;

import com.fundpilot.backend.investmentplan.domain.investmentplan.InvestmentPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PortfolioFund 作废后停止后续计划执行，历史计划和账目保留审计。 */
@Service
@RequiredArgsConstructor
public class InvestmentPlanLifecycleCommandHandler {
    private final InvestmentPlanRepository plans;

    @Transactional
    public void portfolioFundVoided(long portfolioFundId) {
        plans.findByPortfolioFundId(portfolioFundId).forEach(plan -> {
            if (plan.enabled()) {
                plan.disableForVoidedPortfolioFund();
                plans.save(plan);
            }
        });
    }
}
