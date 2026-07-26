package com.fundpilot.backend.marketdata.application.command.navpublishing;

import com.fundpilot.backend.marketdata.application.event.publishednav.NavPublished;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavEventGateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NavPublishingCommandHandlerTest {
    private static final Instant NOW = Instant.parse("2026-07-26T12:00:00Z");

    @Test
    void publishesOnlyNewerNavsInDateOrderWithCompleteEvent() {
        PublishedNavRepository repository = mock(PublishedNavRepository.class);
        PublishedNavEventGateway events = mock(PublishedNavEventGateway.class);
        NavPublishingCommandHandler handler = new NavPublishingCommandHandler(repository, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findLatestByProductId(11L)).thenReturn(Optional.of(nav("2026-07-24T00:00:00Z")));
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = handler.publishNewer(9L, 11L, "000001", List.of(
                candidate("2026-07-26T00:00:00Z", "1.30"),
                candidate("2026-07-24T00:00:00Z", "1.10"),
                candidate("2026-07-25T00:00:00Z", "1.20")));

        assertThat(result).extracting(NavPublishingCommandHandler.PublishedNavResult::navDate)
                .containsExactly(Instant.parse("2026-07-25T00:00:00Z"),
                        Instant.parse("2026-07-26T00:00:00Z"));
        verify(events).publishNavPublished(new NavPublished(11L, "000001",
                Instant.parse("2026-07-25T00:00:00Z"), new BigDecimal("1.20"),
                new BigDecimal("1.20"), NOW));
    }

    @Test
    void duplicateOrOlderCandidatesDoNotWriteOrPublish() {
        PublishedNavRepository repository = mock(PublishedNavRepository.class);
        PublishedNavEventGateway events = mock(PublishedNavEventGateway.class);
        NavPublishingCommandHandler handler = new NavPublishingCommandHandler(repository, events,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findLatestByProductId(11L)).thenReturn(Optional.of(nav("2026-07-25T00:00:00Z")));

        assertThat(handler.publishNewer(null, 11L, "000001",
                List.of(candidate("2026-07-25T00:00:00Z", "1.20")))).isEmpty();

        verify(repository, never()).saveAll(anyList());
        verify(events, never()).publishNavPublished(org.mockito.ArgumentMatchers.any());
    }

    private static PublishedNav nav(String date) {
        return new PublishedNav(1L, 9L, 11L, "000001", Instant.parse(date),
                BigDecimal.ONE, BigDecimal.ONE, NOW);
    }
    private static NavPublishingCommandHandler.NavCandidate candidate(String date, String nav) {
        return new NavPublishingCommandHandler.NavCandidate(Instant.parse(date),
                new BigDecimal(nav), new BigDecimal(nav));
    }
}
