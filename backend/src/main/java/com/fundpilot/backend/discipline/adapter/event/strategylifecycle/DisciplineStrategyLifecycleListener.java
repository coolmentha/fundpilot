package com.fundpilot.backend.discipline.adapter.event.strategylifecycle;

import com.fundpilot.backend.accounting.application.event.position.PositionCleared;
import com.fundpilot.backend.accounting.application.event.position.PositionOpened;
import com.fundpilot.backend.discipline.application.command.strategylifecycle.DisciplineStrategyLifecycleCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 提交后独立停用已清仓或已作废组合基金的生效策略。 */
@Component
@RequiredArgsConstructor
public class DisciplineStrategyLifecycleListener {
    private final DisciplineStrategyLifecycleCommandHandler lifecycle;

    @ApplicationModuleListener
    public void onPositionOpened(PositionOpened event) {
        lifecycle.positionOpened(event.portfolioFundId());
    }

    @ApplicationModuleListener
    public void onPositionCleared(PositionCleared event) {
        lifecycle.positionCleared(event.portfolioFundId());
    }

    @ApplicationModuleListener
    public void onPortfolioFundVoided(PortfolioFundVoidedEvent event) {
        lifecycle.portfolioFundVoided(event.portfolioFundId());
    }
}
