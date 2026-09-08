package com.fundpilot.backend.productcatalog.application.command.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway.SourceSnapshot;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.ShareClass;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundResearchWriterTest {
    @Test
    void createsResearchWhenTheFirstSuccessfulSectionArrives() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        Source source = new Source("东方财富", "https://fundf10.eastmoney.com");
        Instant reportDate = Instant.parse("2026-06-30T00:00:00Z");
        Instant fetchedAt = Instant.parse("2026-07-26T08:00:00Z");
        Profile profile = new Profile("指数型", ShareClass.A, null, null, null);
        SourceSnapshot<Profile> fetched = new SourceSnapshot<>(profile, source, reportDate);
        when(repository.findByFundCode("001071")).thenReturn(Optional.empty());
        when(repository.save(any(FundResearch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var writer = new FundResearchWriter(repository);

        FundResearch saved = writer.writeProfile("001071", fetched, fetchedAt);

        ArgumentCaptor<FundResearch> persisted = ArgumentCaptor.captor();
        verify(repository).save(persisted.capture());
        assertThat(saved).isSameAs(persisted.getValue());
        assertThat(saved.fundCode()).isEqualTo("001071");
        assertThat(saved.profile().data()).isSameAs(profile);
        assertThat(saved.profile().source()).isEqualTo(source);
        assertThat(saved.profile().reportDate()).isEqualTo(reportDate);
        assertThat(saved.profile().fetchedAt()).isEqualTo(fetchedAt);
    }
}
