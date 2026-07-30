package com.fundpilot.backend.discipline.application.command.strategymanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategy;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DisciplineStrategyCommandHandlerTest {
    @Test
    void createKeepsCurrentStrategyAndReturnsDraft() {
        DisciplineStrategyRepository strategies = mock(DisciplineStrategyRepository.class);
        StrategyPortfolioFundGateway funds = mock(StrategyPortfolioFundGateway.class);
        when(funds.requireTrackedByLegacyFund(7L, 11L))
                .thenReturn(new StrategyPortfolioFundGateway.PortfolioFund(21L));
        when(strategies.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = new DisciplineStrategyCommandHandler(strategies, funds).create(7L, 11L, input());

        assertThat(result.status()).isEqualTo("PENDING_CALIBRATION");
        assertThat(result.takeProfitPhase()).isNull();
        verify(strategies, never()).findEffectiveByPortfolioFundId(21L);
        verify(strategies).save(org.mockito.ArgumentMatchers.any(DisciplineStrategy.class));
    }

    private static DisciplineStrategyCommandHandler.Input input() {
        return new DisciplineStrategyCommandHandler.Input(
                new BigDecimal("0.15"), new BigDecimal("0.06"), new BigDecimal("0.50"),
                new BigDecimal("0.50"), new BigDecimal("0.20"), 10,
                "BROAD_BASE", 1, false);
    }
}
