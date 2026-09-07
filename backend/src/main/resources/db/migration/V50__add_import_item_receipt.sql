-- Importing owns receipts; business facts and their successful receipt share one transaction.
CREATE TABLE import_item_receipt (
    owner_id BIGINT NOT NULL,
    session_id VARCHAR(128) NOT NULL,
    item_id VARCHAR(512) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('CREATED', 'SKIPPED', 'ADJUSTED')),
    message TEXT NOT NULL,
    portfolio_fund_id BIGINT NOT NULL REFERENCES portfolio_fund(id),
    completed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (owner_id, session_id, item_id)
);
