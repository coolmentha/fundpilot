ALTER TABLE portfolio_return_snapshot ADD COLUMN owner_id BIGINT;
ALTER TABLE portfolio_return_snapshot ADD CONSTRAINT fk_portfolio_snapshot_owner FOREIGN KEY (owner_id) REFERENCES site_user (id);
UPDATE portfolio_return_snapshot SET owner_id = (SELECT id FROM site_user WHERE role = 'ADMIN' ORDER BY id LIMIT 1)
WHERE owner_id IS NULL;
DROP INDEX uq_portfolio_return_snapshot_date;
CREATE UNIQUE INDEX uq_portfolio_return_snapshot_owner_date
    ON portfolio_return_snapshot (owner_id, business_date) WHERE deleted_date IS NULL;
