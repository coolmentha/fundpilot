package com.fundpilot.backend.discipline.infrastructure.persistence.advice;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface DisciplineAdviceJpaRepository extends JpaRepository<DisciplineAdviceJpaEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select advice from DisciplineAdviceJpaEntity advice where advice.id = :id")
    Optional<DisciplineAdviceJpaEntity> findByIdForUpdate(@Param("id") Long id);
    Optional<DisciplineAdviceJpaEntity> findByPortfolioFundIdAndSignalDate(Long portfolioFundId, Instant signalDate);
    List<DisciplineAdviceJpaEntity> findByOwnerIdAndResponseStatusOrderBySignalDateDesc(Long ownerId, String responseStatus);
    List<DisciplineAdviceJpaEntity> findByPortfolioFundIdAndSignalDateGreaterThanEqualAndSignalDateLessThanOrderBySignalDateDesc(Long portfolioFundId, Instant fromInclusive, Instant toExclusive);
    Optional<DisciplineAdviceJpaEntity> findFirstByPortfolioFundIdOrderBySignalDateDesc(Long portfolioFundId);
}
