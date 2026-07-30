package com.fundpilot.backend.productcatalog.infrastructure.persistence.fee;

import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.RedemptionFeeTier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundFeeSchedulePersistenceMapperTest {
    @Test
    void roundTripsTheLegacyRedemptionLadderJsonShape() {
        FundFeeSchedule schedule = FundFeeSchedule.create("001071", new BigDecimal("0.015"),
                new BigDecimal("0.0015"), null,
                List.of(new RedemptionFeeTier(7, new BigDecimal("0.015")),
                        new RedemptionFeeTier(null, BigDecimal.ZERO)), Instant.EPOCH);

        FundFeeScheduleJpaEntity entity = FundFeeSchedulePersistenceMapper.toEntity(schedule);
        entity.setId(1L);
        FundFeeSchedule restored = FundFeeSchedulePersistenceMapper.toDomain(entity);

        assertThat(entity.getRedemptionLadder()).isEqualTo(
                "[{\"maxDays\":7,\"rate\":0.015},{\"maxDays\":null,\"rate\":0}]");
        assertThat(restored.redemptionTiers()).containsExactlyElementsOf(schedule.redemptionTiers());
    }
}
