package com.fundpilot.backend.portfolio.repository;

import com.fundpilot.backend.portfolio.entity.PortfolioReturnSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioReturnSnapshotRepository extends JpaRepository<PortfolioReturnSnapshotEntity, Long> {
    Optional<PortfolioReturnSnapshotEntity> findByOwnerIdAndBusinessDate(Long ownerId, Instant businessDate);
    Optional<PortfolioReturnSnapshotEntity> findTopByOwnerIdAndBusinessDateBeforeOrderByBusinessDateDesc(Long ownerId, Instant businessDate);
    List<PortfolioReturnSnapshotEntity> findByOwnerIdAndBusinessDateBetweenOrderByBusinessDateAsc(Long ownerId, Instant from, Instant to);

    @Modifying
    @Query("update PortfolioReturnSnapshotEntity p set p.ownerId = :ownerId where p.ownerId is null")
    int claimUnowned(@Param("ownerId") Long ownerId);
    Optional<PortfolioReturnSnapshotEntity> findByBusinessDate(Instant businessDate);
    Optional<PortfolioReturnSnapshotEntity> findTopByBusinessDateBeforeOrderByBusinessDateDesc(Instant businessDate);
    List<PortfolioReturnSnapshotEntity> findByBusinessDateBetweenOrderByBusinessDateAsc(Instant from, Instant to);
}
