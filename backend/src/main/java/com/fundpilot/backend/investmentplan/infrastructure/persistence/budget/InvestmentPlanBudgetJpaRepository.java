package com.fundpilot.backend.investmentplan.infrastructure.persistence.budget;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvestmentPlanBudgetJpaRepository extends JpaRepository<InvestmentPlanBudgetJpaEntity, Long> {
    Optional<InvestmentPlanBudgetJpaEntity> findByOwnerId(Long ownerId);
}
