-- Spring Modulith 2.0.7 JPA event publication registry.
-- Matches DefaultJpaEventPublication (@Table(name = "EVENT_PUBLICATION")); Flyway owns the schema because
-- production uses ddl-auto=validate.
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    publication_date TIMESTAMPTZ NOT NULL,
    listener_id VARCHAR(255) NOT NULL,
    serialized_event TEXT NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    completion_date TIMESTAMPTZ,
    last_resubmission_date TIMESTAMPTZ,
    completion_attempts INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32)
);

CREATE INDEX idx_event_publication_incomplete
    ON event_publication (completion_date, publication_date);
CREATE INDEX idx_event_publication_listener_status
    ON event_publication (listener_id, status, publication_date);
