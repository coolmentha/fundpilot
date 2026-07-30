package com.fundpilot.backend.marketdata.adapter.api.publishednav;

import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.query.navhistory.NavHistoryQueryHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublishedNavApi {
    private final NavPublishingCommandHandler commands;
    private final NavHistoryQueryHandler queries;

    public List<PublishedNav> publishNewer(PublishNavs request) {
        return commands.publishNewer(request.legacyFundId(), request.fundProductId(), request.fundCode(),
                request.candidates().stream().map(candidate ->
                        new NavPublishingCommandHandler.NavCandidate(candidate.navDate(), candidate.unitNav(),
                                candidate.accumulatedNav())).toList())
                .stream().map(PublishedNavApi::from).toList();
    }

    public Optional<PublishedNav> latest(long fundProductId) {
        return queries.latest(fundProductId).map(PublishedNavApi::from);
    }

    public List<PublishedNav> latestByProductIds(Set<Long> fundProductIds) {
        return queries.latestByProductIds(fundProductIds).stream().map(PublishedNavApi::from).toList();
    }

    public List<PublishedNav> latestTwoByProductIds(Set<Long> fundProductIds) {
        return queries.latestTwoByProductIds(fundProductIds).stream().map(PublishedNavApi::from).toList();
    }

    public List<PublishedNav> history(long fundProductId, Instant startInclusive, Instant endExclusive) {
        return queries.history(fundProductId, startInclusive, endExclusive).stream()
                .map(PublishedNavApi::from).toList();
    }

    public Optional<BigDecimal> peakAccumulatedNav(long fundProductId, Instant startInclusive) {
        return queries.peakAccumulatedNav(fundProductId, startInclusive);
    }

    private static PublishedNav from(NavPublishingCommandHandler.PublishedNavResult nav) {
        return new PublishedNav(nav.fundProductId(), nav.fundCode(), nav.navDate(), nav.unitNav(),
                nav.accumulatedNav(), nav.firstSeenAt());
    }
    private static PublishedNav from(NavHistoryQueryHandler.NavResult nav) {
        return new PublishedNav(nav.fundProductId(), nav.fundCode(), nav.navDate(), nav.unitNav(),
                nav.accumulatedNav(), nav.firstSeenAt());
    }

    public record PublishNavs(Long legacyFundId, long fundProductId, String fundCode,
                              List<NavCandidate> candidates) {}
    public record NavCandidate(Instant navDate, BigDecimal unitNav, BigDecimal accumulatedNav) {}
    public record PublishedNav(long fundProductId, String fundCode, Instant navDate,
                               BigDecimal unitNav, BigDecimal accumulatedNav, Instant firstSeenAt) {}
}
