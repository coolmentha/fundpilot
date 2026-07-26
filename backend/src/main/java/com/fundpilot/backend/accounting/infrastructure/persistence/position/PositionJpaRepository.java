package com.fundpilot.backend.accounting.infrastructure.persistence.position;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface PositionJpaRepository extends JpaRepository<PositionJpaEntity, Long> {

    Optional<PositionJpaEntity> findByPortfolioFundId(Long portfolioFundId);

    List<PositionJpaEntity> findByPortfolioFundIdIn(Collection<Long> portfolioFundIds);

    List<PositionJpaEntity> findByOwnerId(Long ownerId);
}
