package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class PublishedNavRepositoryImpl implements PublishedNavRepository {
    private final PublishedNavJpaRepository repository;

    @Override
    public Optional<PublishedNav> findLatestByProductId(long fundProductId) {
        return repository.findFirstByFundProductIdOrderByNavDateDesc(fundProductId)
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
        return repository.saveAll(navs.stream().map(PublishedNavPersistenceMapper::toEntity).toList())
                .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }
}
