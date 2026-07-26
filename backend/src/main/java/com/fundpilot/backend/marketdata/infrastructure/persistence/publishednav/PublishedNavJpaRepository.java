package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PublishedNavJpaRepository extends JpaRepository<PublishedNavJpaEntity, Long> {
    Optional<PublishedNavJpaEntity> findFirstByFundProductIdOrderByNavDateDesc(long fundProductId);
    List<PublishedNavJpaEntity> findByFundProductIdAndNavDateGreaterThanEqualAndNavDateLessThanOrderByNavDateAsc(
            long fundProductId, Instant startInclusive, Instant endExclusive);
}
