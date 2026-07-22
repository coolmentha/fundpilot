ALTER TABLE market_indicator_snapshot ADD COLUMN fund_code VARCHAR(255);
UPDATE market_indicator_snapshot s SET fund_code = COALESCE(f.fund_code, 'LEGACY:' || f.id::text) FROM fund f WHERE f.id = s.fund_id;

UPDATE market_indicator_snapshot SET deleted_date = now()
WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (
            PARTITION BY fund_code, snapshot_date ORDER BY id
        ) AS row_num
        FROM market_indicator_snapshot WHERE deleted_date IS NULL
    ) duplicates WHERE row_num > 1
);

DROP INDEX uq_mis_fund_snapshot_date;
CREATE UNIQUE INDEX uq_market_indicator_snapshot_code_daily
    ON market_indicator_snapshot (fund_code, snapshot_date)
    WHERE deleted_date IS NULL;
