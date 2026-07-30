package com.fundpilot.backend.marketdata.domain.publishednav;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface PublishedNavRepository {
    Optional<PublishedNav> findLatestByProductId(long fundProductId);
    List<PublishedNav> findLatestByProductIds(Set<Long> fundProductIds);
    List<PublishedNav> findLatestTwoByProductIds(Set<Long> fundProductIds);
    List<PublishedNav> findByProductIdAndDateRange(long fundProductId, Instant startInclusive,
                                                   Instant endExclusive);
    Optional<java.math.BigDecimal> findPeakAccumulatedNav(long fundProductId, Instant startInclusive);
    List<PublishedNav> saveAll(List<PublishedNav> navs);
}
