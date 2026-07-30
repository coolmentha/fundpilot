package com.fundpilot.backend.insights.application.query.portfolioreturn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshot;
import com.fundpilot.backend.insights.domain.portfolioreturn.PortfolioReturnSnapshotRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PortfolioReturnTrendQueryHandlerTest {
    @Test
    void calculatesIntervalAndMaximumDrawdownFromOwnerSnapshots() {
        var snapshots = mock(PortfolioReturnSnapshotRepository.class);
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-29T00:00:00Z");
        when(snapshots.between(7L, from, to)).thenReturn(List.of(
                snapshot(1L, from, "100", "1000"),
                snapshot(2L, to, "80", "1100")));
        when(snapshots.latestBefore(7L, from)).thenReturn(Optional.of(
                snapshot(3L, Instant.parse("2026-06-30T00:00:00Z"), "90", "900")));

        var result = new PortfolioReturnTrendQueryHandler(snapshots,
                Clock.fixed(to, ZoneOffset.UTC)).find(7L, "30D", from, to);

        assertThat(result.intervalReturn()).isEqualByComparingTo("-10");
        assertThat(result.maximumReturn()).isEqualByComparingTo("100");
        assertThat(result.maximumDrawdown()).isEqualByComparingTo("20");
        assertThat(result.dataSufficient()).isTrue();
    }

    private static PortfolioReturnSnapshot snapshot(long id, Instant date, String totalReturn, String holding) {
        return new PortfolioReturnSnapshot(id, 7L, date, new BigDecimal("1000"), BigDecimal.ZERO,
                BigDecimal.ZERO, new BigDecimal(holding), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(totalReturn), true, "", date);
    }
}
