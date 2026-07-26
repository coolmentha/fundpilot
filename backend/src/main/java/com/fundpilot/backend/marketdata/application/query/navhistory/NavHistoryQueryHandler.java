package com.fundpilot.backend.marketdata.application.query.navhistory;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NavHistoryQueryHandler {
    private final PublishedNavRepository navs;

    @Transactional(readOnly = true)
    public Optional<NavResult> latest(long fundProductId) {
        return navs.findLatestByProductId(fundProductId).map(NavResult::from);
    }

    @Transactional(readOnly = true)
    public List<NavResult> history(long fundProductId, Instant startInclusive, Instant endExclusive) {
        return navs.findByProductIdAndDateRange(fundProductId, startInclusive, endExclusive)
                .stream().map(NavResult::from).toList();
    }

    public record NavResult(long fundProductId, String fundCode, Instant navDate,
                            BigDecimal unitNav, BigDecimal accumulatedNav, Instant firstSeenAt) {
        static NavResult from(PublishedNav nav) {
            return new NavResult(nav.fundProductId(), nav.fundCode(), nav.navDate(), nav.unitNav(),
                    nav.accumulatedNav(), nav.firstSeenAt());
        }
    }
}
