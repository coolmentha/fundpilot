package com.fundpilot.backend.marketdata.application.command.navpublishing;

import com.fundpilot.backend.marketdata.application.event.publishednav.NavPublished;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavEventGateway;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NavPublishingCommandHandler {
    private final PublishedNavRepository navs;
    private final PublishedNavEventGateway events;
    private final Clock clock;

    @Transactional
    public List<PublishedNavResult> publishNewer(Long legacyFundId, long fundProductId,
                                                 String fundCode, List<NavCandidate> candidates) {
        Instant latest = navs.findLatestByProductId(fundProductId)
                .map(PublishedNav::navDate).orElse(Instant.MIN);
        Instant firstSeenAt = clock.instant();
        List<PublishedNav> additions = candidates.stream()
                .filter(candidate -> candidate.navDate() != null && candidate.navDate().isAfter(latest))
                .sorted(Comparator.comparing(NavCandidate::navDate))
                .map(candidate -> PublishedNav.publish(legacyFundId, fundProductId, fundCode,
                        candidate.navDate(), candidate.unitNav(), candidate.accumulatedNav(), firstSeenAt))
                .toList();
        List<PublishedNav> saved = additions.isEmpty() ? List.of() : navs.saveAll(additions);
        saved.forEach(nav -> events.publishNavPublished(new NavPublished(nav.fundProductId(), nav.fundCode(),
                nav.navDate(), nav.unitNav(), nav.accumulatedNav(), nav.firstSeenAt())));
        return saved.stream().map(PublishedNavResult::from).toList();
    }

    public record NavCandidate(Instant navDate, BigDecimal unitNav, BigDecimal accumulatedNav) {}
    public record PublishedNavResult(long fundProductId, String fundCode, Instant navDate,
                                     BigDecimal unitNav, BigDecimal accumulatedNav,
                                     Instant firstSeenAt) {
        static PublishedNavResult from(PublishedNav nav) {
            return new PublishedNavResult(nav.fundProductId(), nav.fundCode(), nav.navDate(),
                    nav.unitNav(), nav.accumulatedNav(), nav.firstSeenAt());
        }
    }
}
