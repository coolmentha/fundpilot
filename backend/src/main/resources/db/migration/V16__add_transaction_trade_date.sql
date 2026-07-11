-- 交易发生时间与审计创建时间分离。存量数据沿用原 created_date 语义。
ALTER TABLE fund_transaction ADD COLUMN trade_date TIMESTAMPTZ;

UPDATE fund_transaction
SET trade_date = created_date
WHERE trade_date IS NULL;

CREATE INDEX idx_fund_transaction_trade_date
    ON fund_transaction(fund_id, trade_date DESC)
    WHERE deleted_date IS NULL;
