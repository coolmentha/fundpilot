ALTER TABLE user_config ADD COLUMN owner_id BIGINT;
ALTER TABLE user_config ADD CONSTRAINT fk_user_config_owner FOREIGN KEY (owner_id) REFERENCES site_user (id);
CREATE INDEX idx_user_config_owner ON user_config (owner_id) WHERE deleted_date IS NULL;
