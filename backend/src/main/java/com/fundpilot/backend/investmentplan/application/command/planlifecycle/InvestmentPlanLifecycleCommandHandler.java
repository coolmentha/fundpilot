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
        // 无论计划是否暂停(enabled=false)都退休为 DRAFT,避免作废基金上的 EFFECTIVE 计划残留卡死
        plans.findByPortfolioFundId(portfolioFundId).forEach(plan -> {
            plan.disableForVoidedPortfolioFund();
            plans.save(plan);
        });
    }
}
