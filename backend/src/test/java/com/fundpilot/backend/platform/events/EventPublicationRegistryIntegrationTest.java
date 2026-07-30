package com.fundpilot.backend.platform.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.PublicationTargetIdentifier;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class EventPublicationRegistryIntegrationTest {
    @Container
    @ServiceConnection
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private EventPublicationRegistry publications;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void retainsFailedPublicationThenCompletesAndCleansIt() {
        var event = new RegistryTestEvent(UUID.randomUUID().toString());
        var target = PublicationTargetIdentifier.of("registry-test-" + event.id());

        publications.store(event, Stream.of(target));
        assertThat(row(event).completionDate()).isNull();

        publications.markFailed(event, target);
        assertThat(row(event).completionAttempts()).isEqualTo(1);

        publications.processIncompletePublications(
                publication -> publication.getEvent().equals(event),
                publication -> {
                    throw new IllegalStateException("injected listener failure");
                },
                Duration.ZERO);
        assertThat(row(event).completionDate()).isNull();
        assertThat(row(event).completionAttempts()).isEqualTo(2);

        publications.markCompleted(event, target);
        assertThat(row(event).completionDate()).isNotNull();

        publications.deleteCompletedPublicationsOlderThan(Duration.ZERO);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM event_publication WHERE listener_id = ?", Integer.class, target.getValue()))
                .isZero();
    }

    private PublicationRow row(RegistryTestEvent event) {
        return jdbc.queryForObject("""
                        SELECT completion_date, completion_attempts
                        FROM event_publication
                        WHERE serialized_event LIKE ?
                        """,
                (rs, rowNum) -> new PublicationRow(rs.getObject("completion_date"), rs.getInt("completion_attempts")),
                "%" + event.id() + "%");
    }

    public record RegistryTestEvent(String id) {}

    private record PublicationRow(Object completionDate, int completionAttempts) {}
}
