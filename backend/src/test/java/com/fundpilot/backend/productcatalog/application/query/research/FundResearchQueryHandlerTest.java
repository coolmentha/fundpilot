package com.fundpilot.backend.productcatalog.application.query.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.ShareClass;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FundResearchQueryHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsEmptyWhenCodeIsBlankOrResearchDoesNotExist() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        when(repository.findByFundCode("001071")).thenReturn(Optional.empty());
        var handler = new FundResearchQueryHandler(repository, CLOCK);

        assertThat(handler.find("  ")).isEmpty();
        verifyNoInteractions(repository);
        assertThat(handler.find(" 001071 ")).isEmpty();
        verify(repository).findByFundCode("001071");
    }

    @Test
    void marksSnapshotStaleOnlyAfterOneDayWithoutLosingItsStatus() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearch research = FundResearch.create("001071");
        research.updateProfile(new Profile("指数型", ShareClass.A, null, null, null),
                new Source("东方财富", "https://fundf10.eastmoney.com"),
                Instant.parse("2026-06-30T00:00:00Z"), NOW.minusSeconds(86401));
        when(repository.findByFundCode("001071")).thenReturn(Optional.of(research));
        var handler = new FundResearchQueryHandler(repository, CLOCK);

        var result = handler.find("001071").orElseThrow();

        assertThat(result.profile().stale()).isTrue();
        assertThat(result.profile().status()).isEqualTo("SUCCESS");
        assertThat(result.profile().data().fundCategory()).isEqualTo("指数型");
        assertThat(result.scale()).isNull();
        assertThat(result.holdings()).isNull();
        assertThat(result.industry()).isNull();
    }
}
