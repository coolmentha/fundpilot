ALTER TABLE fund_nav_history ADD COLUMN fund_code VARCHAR(255);

UPDATE fund_nav_history n SET fund_code = COALESCE(f.fund_code, 'LEGACY:' || f.id::text) FROM fund f WHERE f.id = n.fund_id;

UPDATE fund_nav_history SET deleted_date = now()
WHERE id IN (
    SELECT id FROM (
        SELECT id, row_number() OVER (
            PARTITION BY fund_code, ((nav_date AT TIME ZONE 'UTC')::date)
            ORDER BY id
        ) AS row_num
        FROM fund_nav_history
        WHERE deleted_date IS NULL
    ) duplicates WHERE row_num > 1
);

DROP INDEX uq_fund_nav_history_daily;
CREATE UNIQUE INDEX uq_fund_nav_history_code_daily
    ON fund_nav_history (fund_code, ((nav_date AT TIME ZONE 'UTC')::date))
    WHERE deleted_date IS NULL;
