package com.fundpilot.backend.productcatalog.domain.fee;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FundFeeScheduleTest {
    @Test
    void refreshesProductContractRatesAsOneSchedule() {
        FundFeeSchedule schedule = FundFeeSchedule.create("001071", new BigDecimal("0.015"),
                new BigDecimal("0.0015"), BigDecimal.ZERO,
                List.of(new RedemptionFeeTier(7, new BigDecimal("0.015"))), Instant.EPOCH);

        assertThat(schedule.fundCode()).isEqualTo("001071");
        assertThat(schedule.discountRate()).isEqualByComparingTo("0.0015");
        assertThat(schedule.redemptionTiers()).hasSize(1);
    }

    @Test
    void rejectsNegativeRates() {
        assertThatThrownBy(() -> FundFeeSchedule.create("001071", null,
                new BigDecimal("-0.001"), null, List.of(), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为负数");
    }
}
