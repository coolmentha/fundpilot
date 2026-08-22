package com.fundpilot.backend.discipline.application.command.strategylifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import com.fundpilot.backend.discipline.domain.strategy.StrategyParamStatus;
import com.fundpilot.backend.discipline.domain.strategy.TakeProfitPhase;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisciplineStrategyLifecycleCommandHandlerTest {
    @Mock
    private DisciplineStrategyRepository strategies;

    @Test
    void positionCleared_retiresEffectiveStrategy() {
        DisciplineStrategy strategy = strategy();
        when(strategies.findEffectiveByPortfolioFundId(10L)).thenReturn(Optional.of(strategy));

        new DisciplineStrategyLifecycleCommandHandler(strategies).positionCleared(10L);

        assertThat(strategy.status()).isEqualTo(StrategyParamStatus.PENDING_CALIBRATION);
        verify(strategies).save(strategy);
    }

    @Test
    void positionOpened_startsFreshCycleWithoutActivatingRetiredStrategy() {
        DisciplineStrategy strategy = strategy();
        when(strategies.findEffectiveByPortfolioFundId(10L)).thenReturn(Optional.of(strategy));

        new DisciplineStrategyLifecycleCommandHandler(strategies).positionOpened(10L);

        assertThat(strategy.status()).isEqualTo(StrategyParamStatus.EFFECTIVE);
        assertThat(strategy.takeProfitPhase()).isEqualTo(TakeProfitPhase.ACCUMULATING);
        verify(strategies).save(strategy);
    }

    @Test
    void portfolioFundVoided_withoutEffectiveStrategy_isIdempotent() {
        when(strategies.findEffectiveByPortfolioFundId(10L)).thenReturn(Optional.empty());

        new DisciplineStrategyLifecycleCommandHandler(strategies).portfolioFundVoided(10L);

        verify(strategies).findEffectiveByPortfolioFundId(10L);
    }

    private static DisciplineStrategy strategy() {
        DisciplineStrategy strategy = DisciplineStrategy.create(10L, 1L, new DisciplineStrategy.Input(
                new BigDecimal("0.10"), new BigDecimal("0.08"), new BigDecimal("0.10"),
                new BigDecimal("0.20"), new BigDecimal("0.50"), 5));
        strategy.activate();
        return strategy;
    }
}
