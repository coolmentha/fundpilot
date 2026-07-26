package com.fundpilot.backend.accounting.infrastructure.persistence.lot;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

interface LotRedemptionJpaRepository extends JpaRepository<LotRedemptionJpaEntity, Long> {

    List<LotRedemptionJpaEntity> findBySellTransactionIdIn(Collection<Long> sellTransactionIds);

    List<LotRedemptionJpaEntity> findByLotIdIn(Collection<Long> lotIds);
}
