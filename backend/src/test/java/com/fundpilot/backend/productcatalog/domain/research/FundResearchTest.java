package com.fundpilot.backend.productcatalog.domain.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundResearchTest {
    @Test
    void timeoutStatusKeepsLastSuccessfulHoldingsAndTimestamp() {
        FundResearch research = FundResearch.create("001071");
        Holdings holdings = new Holdings(List.of(), null, null, null,
                java.math.BigDecimal.ZERO, false, null, FundResearch.LookThroughQuality.DIRECT);
        Instant fetchedAt = Instant.parse("2026-09-01T00:00:00Z");
        research.updateHoldings(holdings, new Source("东方财富", "https://example.test"),
                Instant.parse("2026-06-30T00:00:00Z"), fetchedAt);

        research.failHoldings(new Source("东方财富", "https://example.test"));

        assertThat(research.holdings().status()).isEqualTo(FundResearch.FetchStatus.FAILED);
        assertThat(research.holdings().data()).isSameAs(holdings);
        assertThat(research.holdings().fetchedAt()).isEqualTo(fetchedAt);
    }
}
