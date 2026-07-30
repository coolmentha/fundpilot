package com.fundpilot.backend.discipline.application.command.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.application.gateway.classification.ClassificationPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassification;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationSource;
import org.junit.jupiter.api.Test;

class DisciplineClassificationCommandHandlerTest {
    @Test
    void persistsUserClassificationAfterPortfolioOwnershipCheck() {
        ClassificationPortfolioFundGateway portfolioFunds = mock(ClassificationPortfolioFundGateway.class);
        DisciplineClassificationRepository classifications = mock(DisciplineClassificationRepository.class);
        when(classifications.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        new DisciplineClassificationCommandHandler(portfolioFunds, classifications)
                .set(7L, 12L, "SECTOR", "USER_CUSTOMIZED");

        verify(portfolioFunds).requireTracked(7L, 12L);
        verify(classifications).save(org.mockito.ArgumentMatchers.argThat(value -> {
            assertThat(value).isEqualTo(new DisciplineClassification(12L, 7L, DisciplineCategory.SECTOR,
                    DisciplineClassificationSource.USER_CUSTOMIZED));
            return true;
        }));
    }
}
