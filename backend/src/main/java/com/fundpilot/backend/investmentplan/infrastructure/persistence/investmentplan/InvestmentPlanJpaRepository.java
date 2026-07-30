package com.fundpilot.backend.investmentplan.infrastructure.persistence.investmentplan;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface InvestmentPlanJpaRepository extends JpaRepository<InvestmentPlanJpaEntity, Long> {
    List<InvestmentPlanJpaEntity> findByStatusAndEnabledTrue(String status);
    List<InvestmentPlanJpaEntity> findByPortfolioFundId(Long portfolioFundId);
    Optional<InvestmentPlanJpaEntity> findByPortfolioFundIdAndStatus(Long portfolioFundId, String status);
    List<InvestmentPlanJpaEntity> findByOwnerIdOrderById(Long ownerId);
}
