ALTER TABLE user_config
    ADD COLUMN total_capital NUMERIC(19, 8);

ALTER TABLE user_config
    ADD CONSTRAINT ck_user_config_total_capital
        CHECK (total_capital IS NULL OR total_capital > 0);

ALTER TABLE fund
    ADD COLUMN max_position_ratio NUMERIC(8, 6) NOT NULL DEFAULT 0.30;

ALTER TABLE fund
    ADD CONSTRAINT ck_fund_max_position_ratio
        CHECK (max_position_ratio > 0 AND max_position_ratio <= 0.30);
