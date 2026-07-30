package com.fundpilot.backend.discipline.application.query.strategymanagement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.application.gateway.strategymanagement.StrategyPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassification;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationSource;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DisciplineStrategyQueryHandlerTest {
    @Test
    void recommendationUsesFinalDisciplineClassification() {
        var strategies = mock(DisciplineStrategyRepository.class);
        var funds = mock(StrategyPortfolioFundGateway.class);
        var classifications = mock(DisciplineClassificationRepository.class);
        when(funds.requireTrackedByLegacyFund(7L, 21L))
                .thenReturn(new StrategyPortfolioFundGateway.PortfolioFund(31L));
        when(classifications.findByPortfolioFundIds(7L, Set.of(31L))).thenReturn(List.of(
                new DisciplineClassification(31L, 7L, DisciplineCategory.SECTOR,
                        DisciplineClassificationSource.USER_CUSTOMIZED)));

        var result = new DisciplineStrategyQueryHandler(strategies, funds, classifications)
                .recommendation(7L, 21L);

        assertThat(result.category()).isEqualTo("SECTOR");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.profitActivationPercent()).isEqualByComparingTo(new BigDecimal("0.20"));
        assertThat(result.minimumHoldingPercent()).isEqualByComparingTo(new BigDecimal("0.40"));
    }
}
