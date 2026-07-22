ALTER TABLE fund ADD COLUMN owner_id BIGINT;
ALTER TABLE fund ADD CONSTRAINT fk_fund_owner FOREIGN KEY (owner_id) REFERENCES site_user (id);
CREATE INDEX idx_fund_owner ON fund (owner_id) WHERE deleted_date IS NULL;
