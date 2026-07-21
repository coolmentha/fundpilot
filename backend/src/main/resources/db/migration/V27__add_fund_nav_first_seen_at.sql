ALTER TABLE fund_nav_history
    ADD COLUMN first_seen_at TIMESTAMPTZ;

-- 历史数据无法还原外部实际披露时刻，使用本平台原始入库时间作为首次发现时间。
UPDATE fund_nav_history
SET first_seen_at = created_date
WHERE first_seen_at IS NULL;

ALTER TABLE fund_nav_history
    ALTER COLUMN first_seen_at SET NOT NULL;
