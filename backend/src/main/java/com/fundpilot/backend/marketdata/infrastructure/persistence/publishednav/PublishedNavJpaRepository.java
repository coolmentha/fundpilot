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
    /**
     * Uses a window function instead of counting newer rows for every NAV candidate.
     * The latter becomes quadratic as the NAV history grows.
     */
    @Query(value = """
            select ranked.*
            from (
                select n.*, row_number() over (
                    partition by n.fund_product_id order by n.nav_date desc
                ) as nav_rank
                from fund_nav_history n
                where n.fund_product_id in (:fundProductIds)
                  and n.deleted_date is null
            ) ranked
            where ranked.nav_rank <= 2
            order by ranked.fund_product_id, ranked.nav_date desc
            """, nativeQuery = true)
    List<PublishedNavJpaEntity> findLatestTwoByFundProductIds(@Param("fundProductIds") Set<Long> fundProductIds);
    List<PublishedNavJpaEntity> findByFundProductIdAndNavDateGreaterThanEqualAndNavDateLessThanOrderByNavDateAsc(
            long fundProductId, Instant startInclusive, Instant endExclusive);

    @Query("select max(n.accumulatedNav) from PublishedNavJpaEntity n where n.fundProductId = :fundProductId "
            + "and (:startInclusive is null or n.navDate >= :startInclusive)")
    Optional<java.math.BigDecimal> findPeakAccumulatedNav(@Param("fundProductId") long fundProductId,
                                                          @Param("startInclusive") Instant startInclusive);
}
