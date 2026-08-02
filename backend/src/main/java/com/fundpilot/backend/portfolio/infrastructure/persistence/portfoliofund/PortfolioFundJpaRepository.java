package com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund;

import com.fundpilot.backend.portfolio.domain.portfoliofund.PortfolioFundValidity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface PortfolioFundJpaRepository extends JpaRepository<PortfolioFundJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PortfolioFundJpaEntity p where p.id = :id")
    Optional<PortfolioFundJpaEntity> findByIdForUpdate(@Param("id") Long id);

    Optional<PortfolioFundJpaEntity> findByOwnerIdAndFundProductIdAndValidity(
            long ownerId, long fundProductId, PortfolioFundValidity validity);

    Optional<PortfolioFundJpaEntity> findByLegacyFundId(long legacyFundId);

    List<PortfolioFundJpaEntity> findByOwnerIdOrderById(long ownerId);

    List<PortfolioFundJpaEntity> findByValidityOrderById(PortfolioFundValidity validity);
}
