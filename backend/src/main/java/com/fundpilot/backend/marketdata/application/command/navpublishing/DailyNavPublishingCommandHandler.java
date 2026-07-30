package com.fundpilot.backend.marketdata.application.command.navpublishing;

import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyNavPublishingCommandHandler {
    private final TrackedNavProductGateway products;
    private final PublishedNavSourceGateway source;
    private final PublishedNavRepository navs;
    private final NavPublishingCommandHandler publisher;
    private final Clock clock;

    public void publishToday() {
        publishForDate(ChinaTradingDate.toUtcDate(clock.instant()));
    }

    public void publishForDate(Instant targetDate) {
        Instant normalized = ChinaTradingDate.toUtcDate(targetDate);
        int published = 0;
        int skipped = 0;
        for (TrackedNavProductGateway.TrackedProduct product : products.findAll()) {
            try {
                if (publishOne(product, normalized)) published++; else skipped++;
            } catch (RuntimeException ex) {
                skipped++;
                log.warn("确认产品 {} 净值失败,跳过: {}", product.fundProductId(), ex.getMessage());
            }
        }
        log.info("交易日 {} 净值确认完成:新落库 {} 只,跳过 {} 只", normalized, published, skipped);
    }

    private boolean publishOne(TrackedNavProductGateway.TrackedProduct product, Instant targetDate) {
        if (!supportsStandardNav(product)) return false;
        Instant latest = navs.findLatestByProductId(product.fundProductId())
                .map(PublishedNav::navDate).orElse(Instant.MIN);
        if (!latest.isBefore(targetDate)) return false;
        List<NavPublishingCommandHandler.NavCandidate> candidates = source.fetchHistory(product.fundCode()).stream()
                .filter(nav -> nav.navDate() != null && nav.navDate().isAfter(latest)
                        && !nav.navDate().isAfter(targetDate))
                .sorted(Comparator.comparing(PublishedNavSourceGateway.NavSnapshot::navDate))
                .map(nav -> new NavPublishingCommandHandler.NavCandidate(
                        nav.navDate(), nav.unitNav(), nav.accumulatedNav()))
                .toList();
        return !publisher.publishNewer(product.legacyFundId(), product.fundProductId(),
                product.fundCode(), candidates).isEmpty();
    }

    private static boolean supportsStandardNav(TrackedNavProductGateway.TrackedProduct product) {
        if (product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.MONEY_MARKET
                || product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.REIT) return false;
        if (product.fundName() == null) return true;
        String name = product.fundName().toUpperCase(Locale.ROOT);
        return !name.contains("货币") && !name.contains("REIT") && !name.contains("不动产投资信托");
    }
}
