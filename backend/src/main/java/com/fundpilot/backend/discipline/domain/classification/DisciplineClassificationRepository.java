package com.fundpilot.backend.discipline.domain.classification;

import java.util.List;
import java.util.Set;

public interface DisciplineClassificationRepository {
    List<DisciplineClassification> findByPortfolioFundIds(long ownerId, Set<Long> portfolioFundIds);

    DisciplineClassification save(DisciplineClassification classification);
}
