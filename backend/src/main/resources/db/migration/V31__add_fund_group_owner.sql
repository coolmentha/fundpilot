ALTER TABLE fund_group ADD COLUMN owner_id BIGINT;
ALTER TABLE fund_group ADD CONSTRAINT fk_fund_group_owner FOREIGN KEY (owner_id) REFERENCES site_user (id);
CREATE INDEX idx_fund_group_owner ON fund_group (owner_id) WHERE deleted_date IS NULL;
