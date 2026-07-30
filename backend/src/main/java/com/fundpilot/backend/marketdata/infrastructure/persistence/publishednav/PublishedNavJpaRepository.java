package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface PublishedNavJpaRepository extends JpaRepository<PublishedNavJpaEntity, Long> {
    Optional<PublishedNavJpaEntity> findFirstByFundProductIdOrderByNavDateDesc(long fundProductId);

    @Query("select n from PublishedNavJpaEntity n where n.fundProductId in :fundProductIds and n.navDate = "
            + "(select max(other.navDate) from PublishedNavJpaEntity other "
            + "where other.fundProductId = n.fundProductId)")
    List<PublishedNavJpaEntity> findLatestByFundProductIds(@Param("fundProductIds") Set<Long> fundProductIds);
    @Query("select n from PublishedNavJpaEntity n where n.fundProductId in :fundProductIds and "
            + "(select count(other) from PublishedNavJpaEntity other where other.fundProductId = n.fundProductId "
            + "and other.navDate > n.navDate) < 2 order by n.fundProductId, n.navDate desc")
    List<PublishedNavJpaEntity> findLatestTwoByFundProductIds(@Param("fundProductIds") Set<Long> fundProductIds);
    List<PublishedNavJpaEntity> findByFundProductIdAndNavDateGreaterThanEqualAndNavDateLessThanOrderByNavDateAsc(
            long fundProductId, Instant startInclusive, Instant endExclusive);

    @Query("select max(n.accumulatedNav) from PublishedNavJpaEntity n where n.fundProductId = :fundProductId "
            + "and (:startInclusive is null or n.navDate >= :startInclusive)")
    Optional<java.math.BigDecimal> findPeakAccumulatedNav(@Param("fundProductId") long fundProductId,
                                                          @Param("startInclusive") Instant startInclusive);
}
