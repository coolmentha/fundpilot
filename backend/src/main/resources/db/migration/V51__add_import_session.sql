CREATE TABLE import_session (
    id VARCHAR(36) PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('WAITING', 'CONNECTED', 'PROCESSING', 'COMPLETED', 'EXPIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL
);

CREATE INDEX idx_import_session_owner_updated ON import_session (owner_id, updated_at DESC);
CREATE INDEX idx_import_session_processing ON import_session (updated_at) WHERE status = 'PROCESSING';
