package com.fundpilot.backend.discipline.infrastructure.persistence.classification;

import com.fundpilot.backend.discipline.domain.classification.DisciplineCategory;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassification;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationRepository;
import com.fundpilot.backend.discipline.domain.classification.DisciplineClassificationSource;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class DisciplineClassificationRepositoryImpl implements DisciplineClassificationRepository {
    private final DisciplineClassificationJpaRepository classifications;

    @Override
    public List<DisciplineClassification> findByPortfolioFundIds(long ownerId, Set<Long> portfolioFundIds) {
        return classifications.findByOwnerIdAndPortfolioFundIdIn(ownerId, portfolioFundIds).stream()
                .map(value -> new DisciplineClassification(value.getPortfolioFundId(), value.getOwnerId(),
                        DisciplineCategory.valueOf(value.getCategory()),
                        DisciplineClassificationSource.valueOf(value.getSource())))
                .toList();
    }

    @Override
    public DisciplineClassification save(DisciplineClassification classification) {
        DisciplineClassificationJpaEntity entity = classifications
                .findByPortfolioFundId(classification.portfolioFundId())
                .orElseGet(DisciplineClassificationJpaEntity::new);
        if (entity.getOwnerId() != null && entity.getOwnerId() != classification.ownerId()) {
            throw new IllegalStateException("纪律分类所有者不匹配: " + classification.portfolioFundId());
        }
        entity.setPortfolioFundId(classification.portfolioFundId());
        entity.setOwnerId(classification.ownerId());
        entity.setCategory(classification.category().name());
        entity.setSource(classification.source().name());
        DisciplineClassificationJpaEntity saved = classifications.save(entity);
        return new DisciplineClassification(saved.getPortfolioFundId(), saved.getOwnerId(),
                DisciplineCategory.valueOf(saved.getCategory()),
                DisciplineClassificationSource.valueOf(saved.getSource()));
    }
}
