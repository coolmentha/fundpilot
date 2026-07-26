package com.fundpilot.backend.marketdata.domain.publishednav;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublishedNavTest {
    @Test
    void createsFrameworkFreePublishedNav() {
        PublishedNav nav = PublishedNav.publish(9L, 11L, " 000001 ",
                Instant.parse("2026-07-25T00:00:00Z"), new BigDecimal("1.12345678"),
                new BigDecimal("1.23456789"), Instant.parse("2026-07-25T12:00:00Z"));

        assertThat(nav.fundCode()).isEqualTo("000001");
        assertThat(nav.fundProductId()).isEqualTo(11L);
    }

    @Test
    void rejectsNonPositiveUnitNav() {
        assertThatThrownBy(() -> PublishedNav.publish(null, 11L, "000001",
                Instant.parse("2026-07-25T00:00:00Z"), BigDecimal.ZERO, null,
                Instant.parse("2026-07-25T12:00:00Z")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("单位净值");
    }
}
