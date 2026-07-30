package com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundValidity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface PortfolioFundJpaRepository extends JpaRepository<PortfolioFundJpaEntity, Long> {
    Optional<PortfolioFundJpaEntity> findByOwnerIdAndFundProductIdAndValidity(
            long ownerId, long fundProductId, PortfolioFundValidity validity);

    Optional<PortfolioFundJpaEntity> findByLegacyFundId(long legacyFundId);

    List<PortfolioFundJpaEntity> findByOwnerIdOrderById(long ownerId);

    List<PortfolioFundJpaEntity> findByValidityOrderById(PortfolioFundValidity validity);
}
