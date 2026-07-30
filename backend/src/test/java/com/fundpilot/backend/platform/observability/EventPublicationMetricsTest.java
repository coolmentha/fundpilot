package com.fundpilot.backend.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EventPublicationMetricsTest {
    @Test
    void exposesPublicationBacklogGauges() {
        JdbcTemplate jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(3L, 1L);
        when(jdbc.queryForObject(anyString(), eq(Double.class))).thenReturn(42.5D);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new EventPublicationMetrics(jdbc).bindTo(registry);

        assertThat(registry.get("modulith_event_publications_incomplete").gauge().value()).isEqualTo(3D);
        assertThat(registry.get("modulith_event_publication_oldest_age_seconds").gauge().value()).isEqualTo(42.5D);
        assertThat(registry.get("modulith_event_publications_retry_pending").gauge().value()).isEqualTo(1D);
    }
}
