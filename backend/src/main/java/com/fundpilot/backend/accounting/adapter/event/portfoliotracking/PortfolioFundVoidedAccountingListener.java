package com.fundpilot.backend.accounting.adapter.event.portfoliotracking;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/** 作废只清除 Accounting 的可重建持仓投影，账本与 lot 审计记录保持不变。 */
@Component
@RequiredArgsConstructor
public class PortfolioFundVoidedAccountingListener {
    private final PositionCommandHandler positions;

    @ApplicationModuleListener
    public void onVoided(PortfolioFundVoidedEvent event) {
        positions.removeVoidedProjection(event.portfolioFundId());
    }
}
