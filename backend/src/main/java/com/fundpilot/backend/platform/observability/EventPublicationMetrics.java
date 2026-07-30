package com.fundpilot.backend.platform.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Exposes Spring Modulith publication backlog state through the existing Prometheus endpoint. */
@Component
@RequiredArgsConstructor
public class EventPublicationMetrics implements MeterBinder {
    private static final String INCOMPLETE = "SELECT count(*) FROM event_publication WHERE completion_date IS NULL";
    private static final String OLDEST_AGE_SECONDS = """
            SELECT COALESCE(EXTRACT(EPOCH FROM current_timestamp - min(publication_date)), 0)
            FROM event_publication WHERE completion_date IS NULL
            """;
    private static final String RETRY_PENDING = """
            SELECT count(*) FROM event_publication
            WHERE completion_date IS NULL AND completion_attempts > 0
            """;

    private final JdbcTemplate jdbc;

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("modulith_event_publications_incomplete", this, EventPublicationMetrics::incomplete)
                .description("Incomplete Spring Modulith event publications")
                .register(registry);
        Gauge.builder("modulith_event_publication_oldest_age_seconds", this, EventPublicationMetrics::oldestAgeSeconds)
                .description("Age of the oldest incomplete Spring Modulith event publication")
                .register(registry);
        Gauge.builder("modulith_event_publications_retry_pending", this, EventPublicationMetrics::retryPending)
                .description("Incomplete Spring Modulith event publications with retry attempts")
                .register(registry);
    }

    double incomplete() {
        Long count = jdbc.queryForObject(INCOMPLETE, Long.class);
        return count == null ? 0 : count;
    }

    double oldestAgeSeconds() {
        Double seconds = jdbc.queryForObject(OLDEST_AGE_SECONDS, Double.class);
        return seconds == null ? 0 : seconds;
    }

    double retryPending() {
        Long count = jdbc.queryForObject(RETRY_PENDING, Long.class);
        return count == null ? 0 : count;
    }
}
