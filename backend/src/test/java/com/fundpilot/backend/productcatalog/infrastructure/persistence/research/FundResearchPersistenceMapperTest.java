package com.fundpilot.backend.productcatalog.infrastructure.persistence.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.AmountUnit;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.DatedAmount;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.ShareClass;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FundResearchPersistenceMapperTest {
    @Test
    void roundTripsNullableRecordSnapshotsIncludingInstant() {
        FundResearch research = FundResearch.create("005919");
        Instant reportDate = Instant.parse("2026-06-30T00:00:00Z");
        research.updateProfile(new Profile("指数型-股票", ShareClass.C, null, null, null),
                new Source("东方财富", "https://example.test/profile"), null, Instant.EPOCH);
        research.updateScale(new Scale(new DatedAmount(new BigDecimal("3.93"),
                        AmountUnit.HUNDRED_MILLION_SHARES, reportDate), null, null),
                new Source("东方财富", "https://example.test/scale"), reportDate, Instant.EPOCH);

        FundResearchJpaEntity entity = FundResearchPersistenceMapper.toEntity(research);
        entity.setId(1L);
        FundResearch restored = FundResearchPersistenceMapper.toDomain(entity);

        assertThat(restored.profile().data().shareClass()).isEqualTo(ShareClass.C);
        assertThat(restored.scale().data().shareScale().asOf()).isEqualTo(reportDate);
        assertThat(restored.scale().data().combinedAssetScale()).isNull();
        assertThat(restored.holdings()).isNull();
    }
}
