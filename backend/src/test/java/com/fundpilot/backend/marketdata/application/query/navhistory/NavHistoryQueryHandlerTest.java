package com.fundpilot.backend.marketdata.application.query.navhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NavHistoryQueryHandlerTest {
    @Test
    void peakAccumulatedNav_preservesHoldingPeriodStart() {
        PublishedNavRepository navs = mock(PublishedNavRepository.class);
        Instant openedAt = Instant.parse("2026-07-01T00:00:00Z");
        when(navs.findPeakAccumulatedNav(9L, openedAt)).thenReturn(Optional.of(new BigDecimal("2.31")));

        assertThat(new NavHistoryQueryHandler(navs).peakAccumulatedNav(9L, openedAt))
                .contains(new BigDecimal("2.31"));
        verify(navs).findPeakAccumulatedNav(9L, openedAt);
    }
}
