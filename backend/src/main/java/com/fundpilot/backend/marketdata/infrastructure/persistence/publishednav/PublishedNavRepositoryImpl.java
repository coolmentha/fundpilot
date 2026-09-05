package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PublishedNavRepositoryImpl implements PublishedNavRepository {
    private final PublishedNavJpaRepository repository;
    private final JdbcTemplate jdbc;

    @Override
    public Optional<PublishedNav> findLatestByProductId(long fundProductId) {
        return repository.findFirstByFundProductIdAndNavIsNotNullAndNavGreaterThanOrderByNavDateDesc(
                        fundProductId, BigDecimal.ZERO)
                .map(PublishedNavPersistenceMapper::toDomain);
    }

    @Override
    public List<PublishedNav> findLatestByProductIds(Set<Long> fundProductIds) {
        return fundProductIds.isEmpty() ? List.of() : repository.findLatestByFundProductIds(fundProductIds)
                .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<PublishedNav> findLatestTwoByProductIds(Set<Long> fundProductIds) {
        return fundProductIds.isEmpty() ? List.of() : repository.findLatestTwoByFundProductIds(fundProductIds)
                .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<PublishedNav> findLatestTwoByProductIdsAt(Set<Long> fundProductIds, Instant businessDate) {
        return fundProductIds.isEmpty() ? List.of()
                : repository.findLatestTwoByFundProductIdsAt(fundProductIds,
                        BusinessDay.toDateLabel(businessDate).plus(1, java.time.temporal.ChronoUnit.DAYS),
                        BusinessDay.endExclusive(businessDate))
                        .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<PublishedNav> findByProductIdAndDateRange(long fundProductId, Instant startInclusive,
                                                          Instant endExclusive) {
        return repository
                .findByFundProductIdAndNavDateGreaterThanEqualAndNavDateLessThanOrderByNavDateAsc(
                        fundProductId, startInclusive, endExclusive)
                .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }

    @Override
    public Optional<java.math.BigDecimal> findPeakAccumulatedNav(long fundProductId, Instant startInclusive) {
        return repository.findPeakAccumulatedNav(fundProductId, startInclusive);
    }

    @Override
    public List<PublishedNav> saveAll(List<PublishedNav> navs) {
        if (navs.isEmpty()) return List.of();
        List<PublishedNav> inserted = new ArrayList<>();
        for (PublishedNav nav : navs) {
            int rows = jdbc.update("""
                    INSERT INTO fund_nav_history
                        (fund_id, fund_product_id, fund_code, nav_date, nav, accumulated_nav,
                         first_seen_at, version, created_date, updated_date)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    ON CONFLICT (fund_product_id, ((nav_date AT TIME ZONE 'UTC')::date))
                        WHERE deleted_date IS NULL
                    DO NOTHING
                    """, nav.legacyFundId(), nav.fundProductId(), nav.fundCode(),
                    Timestamp.from(nav.navDate()), nav.unitNav(), nav.accumulatedNav(),
                    Timestamp.from(nav.firstSeenAt()));
            if (rows == 1) inserted.add(nav);
        }
        return inserted;
    }
}
