package com.fundpilot.backend.discipline.application.command.strategylifecycle;

import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 清仓或作废后停用该组合基金仍在生效的纪律策略。 */
@Service
@RequiredArgsConstructor
public class DisciplineStrategyLifecycleCommandHandler {
    private final DisciplineStrategyRepository strategies;

    @Transactional
    public void positionCleared(long portfolioFundId) {
        retireEffective(portfolioFundId);
    }

    @Transactional
    public void positionOpened(long portfolioFundId) {
        strategies.findEffectiveByPortfolioFundId(portfolioFundId).ifPresent(strategy -> {
            strategy.positionOpened();
            strategies.save(strategy);
        });
    }

    @Transactional
    public void portfolioFundVoided(long portfolioFundId) {
        retireEffective(portfolioFundId);
    }

    private void retireEffective(long portfolioFundId) {
        strategies.findEffectiveByPortfolioFundId(portfolioFundId).ifPresent(strategy -> {
            strategy.retire();
            strategies.save(strategy);
        });
    }
}
