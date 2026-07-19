package com.fundpilot.backend.portfolio.service;

import com.fundpilot.backend.portfolio.entity.PortfolioReturnSnapshotEntity;
import com.fundpilot.backend.portfolio.repository.PortfolioReturnSnapshotRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortfolioReturnTrendServiceTest {

    @Test
    void getTrend_区分资金流并计算最大回撤() {
        PortfolioReturnSnapshotRepository repository = mock(PortfolioReturnSnapshotRepository.class);
        Instant from = Instant.parse("2026-07-01T00:00:00Z");
        Instant to = Instant.parse("2026-07-03T00:00:00Z");
        var baseline = row("2026-06-30T00:00:00Z", "1000", "100", "20", "0", "0");
        var first = row("2026-07-01T00:00:00Z", "1100", "130", "120", "0", "0");
        var peak = row("2026-07-02T00:00:00Z", "1100", "160", "150", "0", "0");
        var last = row("2026-07-03T00:00:00Z", "1100", "140", "130", "40", "2");
        when(repository.findTopByBusinessDateBeforeOrderByBusinessDateDesc(from)).thenReturn(Optional.of(baseline));
        when(repository.findByBusinessDateBetweenOrderByBusinessDateAsc(from, to)).thenReturn(List.of(first, peak, last));

        var service = new PortfolioReturnTrendService(repository, mock(PortfolioReturnService.class),
                Clock.fixed(to, ZoneOffset.UTC));
        var result = service.getTrend("30D", from, to);

        assertThat(result.intervalReturn()).isEqualByComparingTo("40");
        assertThat(result.investedAmount()).isEqualByComparingTo("110");
        assertThat(result.redeemedAmount()).isEqualByComparingTo("40");
        assertThat(result.maximumDrawdown()).isEqualByComparingTo("20");
        assertThat(result.dataSufficient()).isTrue();
    }

    private PortfolioReturnSnapshotEntity row(String date, String holding, String totalReturn,
                                                String invested, String redeemed, String fees) {
        var row = new PortfolioReturnSnapshotEntity();
        row.setBusinessDate(Instant.parse(date));
        row.setHoldingAmount(new BigDecimal(holding));
        row.setTotalReturn(new BigDecimal(totalReturn));
        row.setInvestedAmount(new BigDecimal(invested));
        row.setRedeemedAmount(new BigDecimal(redeemed));
        row.setFeeAmount(new BigDecimal(fees));
        row.setValuationComplete(true);
        return row;
    }
}
