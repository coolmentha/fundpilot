package com.fundpilot.backend.discipline.application.query.classification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassification;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationSource;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DisciplineClassificationQueryHandlerTest {
    @Test
    void returnsPortfolioScopedFinalCategories() {
        DisciplineClassificationRepository repository = mock(DisciplineClassificationRepository.class);
        when(repository.findByPortfolioFundIds(7L, Set.of(12L))).thenReturn(List.of(
                new DisciplineClassification(12L, 7L, DisciplineCategory.SECTOR,
                        DisciplineClassificationSource.USER_CUSTOMIZED)));

        var result = new DisciplineClassificationQueryHandler(repository).findByPortfolioFundIds(7L, Set.of(12L));

        assertThat(result).containsExactly(new DisciplineClassificationQueryHandler.Classification(12L, "SECTOR"));
    }

    @Test
    void emptyInputDoesNotQueryRepository() {
        DisciplineClassificationRepository repository = mock(DisciplineClassificationRepository.class);

        assertThat(new DisciplineClassificationQueryHandler(repository).findByPortfolioFundIds(7L, Set.of())).isEmpty();

        verifyNoInteractions(repository);
    }
}
