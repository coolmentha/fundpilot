package com.fundpilot.backend.discipline.application.query.classification;

import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DisciplineClassificationQueryHandler {
    private final DisciplineClassificationRepository classifications;

    @Transactional(readOnly = true)
    public List<Classification> findByPortfolioFundIds(long ownerId, Set<Long> portfolioFundIds) {
        if (ownerId <= 0 || portfolioFundIds == null || portfolioFundIds.isEmpty()) {
            return List.of();
        }
        return classifications.findByPortfolioFundIds(ownerId, portfolioFundIds).stream()
                .map(value -> new Classification(value.portfolioFundId(), value.category().name()))
                .toList();
    }

    public record Classification(long portfolioFundId, String category) {
    }
}
