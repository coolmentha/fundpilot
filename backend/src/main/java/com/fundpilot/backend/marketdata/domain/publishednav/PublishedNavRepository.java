package com.fundpilot.backend.marketdata.domain.publishednav;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PublishedNavRepository {
    Optional<PublishedNav> findLatestByProductId(long fundProductId);
    List<PublishedNav> findByProductIdAndDateRange(long fundProductId, Instant startInclusive,
                                                   Instant endExclusive);
    List<PublishedNav> saveAll(List<PublishedNav> navs);
}
