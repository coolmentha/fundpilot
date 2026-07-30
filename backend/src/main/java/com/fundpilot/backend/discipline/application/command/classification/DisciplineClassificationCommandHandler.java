package com.fundpilot.backend.discipline.application.command.classification;

import com.fundpilot.backend.discipline.application.gateway.classification.ClassificationPortfolioFundGateway;
import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassification;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisciplineClassificationCommandHandler {
    private final ClassificationPortfolioFundGateway portfolioFunds;
    private final DisciplineClassificationRepository classifications;

    @Transactional
    public void set(long ownerId, long portfolioFundId, String category, String source) {
        portfolioFunds.requireTracked(ownerId, portfolioFundId);
        classifications.save(new DisciplineClassification(portfolioFundId, ownerId,
                DisciplineCategory.valueOf(category), DisciplineClassificationSource.valueOf(source)));
    }
}
