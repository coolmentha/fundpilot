package com.fundpilot.backend.portfolio.repository;

import com.fundpilot.backend.portfolio.entity.PortfolioReturnSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PortfolioReturnSnapshotRepository extends JpaRepository<PortfolioReturnSnapshotEntity, Long> {
    Optional<PortfolioReturnSnapshotEntity> findByBusinessDate(Instant businessDate);
    Optional<PortfolioReturnSnapshotEntity> findTopByBusinessDateBeforeOrderByBusinessDateDesc(Instant businessDate);
    List<PortfolioReturnSnapshotEntity> findByBusinessDateBetweenOrderByBusinessDateAsc(Instant from, Instant to);
}
