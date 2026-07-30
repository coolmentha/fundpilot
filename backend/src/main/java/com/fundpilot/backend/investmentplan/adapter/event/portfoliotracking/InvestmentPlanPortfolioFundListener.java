package com.fundpilot.backend.investmentplan.adapter.event.portfoliotracking;

import com.fundpilot.backend.investmentplan.application.command.planlifecycle.InvestmentPlanLifecycleCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InvestmentPlanPortfolioFundListener {
    private final InvestmentPlanLifecycleCommandHandler lifecycle;

    @ApplicationModuleListener
    public void onVoided(PortfolioFundVoidedEvent event) {
        lifecycle.portfolioFundVoided(event.portfolioFundId());
    }
}
