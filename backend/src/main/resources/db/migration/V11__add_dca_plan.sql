-- 定投计划表:用户配置的自动定投计划,DcaSuggestionJob 在定投日自动生成 INVEST 交易。
CREATE TABLE fund_dca_plan (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    fund_id BIGINT NOT NULL REFERENCES fund(id),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    amount NUMERIC(19,8) NOT NULL,
    frequency VARCHAR(32) NOT NULL,
    day_of_week INT,
    day_of_month INT,
    status VARCHAR(32) NOT NULL,
    created_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_date TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_date TIMESTAMPTZ
);

-- 同基金同时最多一份 EFFECTIVE 定投计划。
CREATE UNIQUE INDEX uq_fund_dca_plan_effective ON fund_dca_plan(fund_id) WHERE status='EFFECTIVE' AND deleted_date IS NULL;
CREATE INDEX idx_fund_dca_plan_fund ON fund_dca_plan(fund_id);

-- fund_transaction 加 dca_plan_id:标记定投交易来源计划(手动交易为 null),用于幂等去重。
ALTER TABLE fund_transaction ADD COLUMN dca_plan_id BIGINT;
