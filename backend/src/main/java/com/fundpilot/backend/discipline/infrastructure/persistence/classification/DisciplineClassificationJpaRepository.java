package com.fundpilot.backend.discipline.infrastructure.persistence.classification;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface DisciplineClassificationJpaRepository extends JpaRepository<DisciplineClassificationJpaEntity, Long> {
    List<DisciplineClassificationJpaEntity> findByOwnerIdAndPortfolioFundIdIn(long ownerId, Collection<Long> portfolioFundIds);

    Optional<DisciplineClassificationJpaEntity> findByPortfolioFundId(long portfolioFundId);
}
