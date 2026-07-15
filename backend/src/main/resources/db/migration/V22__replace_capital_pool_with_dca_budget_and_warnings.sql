ALTER TABLE user_config
    DROP CONSTRAINT ck_user_config_total_capital,
    DROP COLUMN total_capital,
    ADD COLUMN monthly_dca_budget NUMERIC(19, 8),
    ADD CONSTRAINT ck_user_config_monthly_dca_budget
        CHECK (monthly_dca_budget IS NULL OR monthly_dca_budget > 0);

ALTER TABLE fund
    DROP CONSTRAINT ck_fund_max_position_ratio;

ALTER TABLE fund
    RENAME COLUMN max_position_ratio TO position_warning_ratio;

ALTER TABLE fund
    ADD COLUMN position_warning_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    ADD CONSTRAINT ck_fund_position_warning_ratio
        CHECK (position_warning_ratio > 0 AND position_warning_ratio <= 1);
