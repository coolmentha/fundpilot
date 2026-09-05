package com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedNavRepositoryIntegrationTest extends AbstractIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-07-30T00:00:00Z");

    @Autowired
    private PublishedNavRepository navs;

    @Autowired
    private FundProductApi products;

    @Test
    void saveAll_conflictsOnSameProductUtcDateAreSkippedWithoutError() {
        long productId = createProduct("conflict");
        PublishedNav original = nav(productId, "conflict", 1);

        List<PublishedNav> first = navs.saveAll(List.of(original));
        List<PublishedNav> second = navs.saveAll(List.of(
                nav(productId, "conflict", 1),
                nav(productId, "conflict", 2)));

        assertThat(first).hasSize(1);
        assertThat(second).extracting(PublishedNav::navDate)
                .containsExactly(BASE_TIME.plus(2, ChronoUnit.DAYS));
        assertThat(navs.findLatestTwoByProductIds(Set.of(productId)))
                .extracting(PublishedNav::navDate)
                .containsExactly(BASE_TIME.plus(2, ChronoUnit.DAYS), BASE_TIME.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void latestTwoByProductIds_returnsTwoNewestNavsPerProductInDescendingOrder() {
        long firstProductId = createProduct("first");
        long secondProductId = createProduct("second");
        navs.saveAll(List.of(
                nav(firstProductId, "first", 1),
                nav(firstProductId, "first", 2),
                nav(firstProductId, "first", 3),
                nav(secondProductId, "second", 4),
                nav(secondProductId, "second", 5),
                nav(secondProductId, "second", 6)));

        List<PublishedNav> result = navs.findLatestTwoByProductIds(Set.of(firstProductId, secondProductId));

        assertThat(result).extracting(PublishedNav::fundProductId, PublishedNav::navDate)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(firstProductId, BASE_TIME.plus(3, ChronoUnit.DAYS)),
                        org.assertj.core.groups.Tuple.tuple(firstProductId, BASE_TIME.plus(2, ChronoUnit.DAYS)),
                        org.assertj.core.groups.Tuple.tuple(secondProductId, BASE_TIME.plus(6, ChronoUnit.DAYS)),
                        org.assertj.core.groups.Tuple.tuple(secondProductId, BASE_TIME.plus(5, ChronoUnit.DAYS)));
    }

    @Test
    void latestTwoAtExcludesFutureNavDatesAndFactsDiscoveredLater() {
        long productId = createProduct("historical");
        navs.saveAll(List.of(
                nav(productId, "historical", 1),
                nav(productId, "historical", 2, BASE_TIME.plus(4, ChronoUnit.DAYS)),
                nav(productId, "historical", 3)));

        assertThat(navs.findLatestTwoByProductIdsAt(Set.of(productId),
                BASE_TIME.plus(2, ChronoUnit.DAYS)))
                .extracting(PublishedNav::navDate)
                .containsExactly(BASE_TIME.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void latestTwoAtUsesBeijingBusinessDayEndForFirstSeenFacts() {
        long includedProductId = createProduct("seen-before-end");
        long excludedProductId = createProduct("seen-at-end");
        navs.saveAll(List.of(
                PublishedNav.publish(null, includedProductId, "included", BASE_TIME,
                        BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-07-30T15:59:59Z")),
                PublishedNav.publish(null, excludedProductId, "excluded", BASE_TIME,
                        BigDecimal.ONE, BigDecimal.ONE, Instant.parse("2026-07-30T16:00:00Z"))));

        assertThat(navs.findLatestTwoByProductIdsAt(
                Set.of(includedProductId, excludedProductId), BASE_TIME))
                .extracting(PublishedNav::fundProductId)
                .containsExactly(includedProductId);
    }

    private long createProduct(String prefix) {
        String suffix = Long.toUnsignedString(System.nanoTime(), 36);
        return products.ensure(new FundProductApi.EnsureProduct(
                "T" + suffix.substring(Math.max(0, suffix.length() - 12)),
                prefix + " test product", null, null)).id();
    }

    private static PublishedNav nav(long productId, String code, int daysAfterBase) {
        Instant navDate = BASE_TIME.plus(daysAfterBase, ChronoUnit.DAYS);
        return PublishedNav.publish(null, productId, code, navDate, BigDecimal.ONE, BigDecimal.ONE, navDate);
    }

    private static PublishedNav nav(long productId, String code, int daysAfterBase, Instant firstSeenAt) {
        Instant navDate = BASE_TIME.plus(daysAfterBase, ChronoUnit.DAYS);
        return PublishedNav.publish(null, productId, code, navDate, BigDecimal.ONE, BigDecimal.ONE, firstSeenAt);
    }
}
