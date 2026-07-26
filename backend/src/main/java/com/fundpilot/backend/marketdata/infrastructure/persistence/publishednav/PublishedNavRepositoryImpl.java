package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    public List<PublishedNav> findByProductIdAndDateRange(long fundProductId, Instant startInclusive,
                                                          Instant endExclusive) {
        return repository
                .findByFundProductIdAndNavDateGreaterThanEqualAndNavDateLessThanOrderByNavDateAsc(
                        fundProductId, startInclusive, endExclusive)
                .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }

    @Override
    public List<PublishedNav> saveAll(List<PublishedNav> navs) {
        return repository.saveAll(navs.stream().map(PublishedNavPersistenceMapper::toEntity).toList())
                .stream().map(PublishedNavPersistenceMapper::toDomain).toList();
    }
}
