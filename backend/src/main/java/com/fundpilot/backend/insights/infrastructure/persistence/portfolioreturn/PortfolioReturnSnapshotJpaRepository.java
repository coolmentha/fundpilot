package com.fundpilot.backend.insights.infrastructure.persistence.portfolioreturn;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioReturnSnapshotJpaRepository
        extends JpaRepository<PortfolioReturnSnapshotJpaEntity, Long> {
    Optional<PortfolioReturnSnapshotJpaEntity> findByOwnerIdAndBusinessDate(long ownerId, Instant businessDate);
    Optional<PortfolioReturnSnapshotJpaEntity> findTopByOwnerIdAndBusinessDateBeforeOrderByBusinessDateDesc(
            long ownerId, Instant businessDate);
    List<PortfolioReturnSnapshotJpaEntity> findByOwnerIdAndBusinessDateBetweenOrderByBusinessDateAsc(
            long ownerId, Instant from, Instant to);
}
