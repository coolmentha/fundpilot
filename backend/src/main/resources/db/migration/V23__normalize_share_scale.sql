-- 基金份额统一为两位，并触发一次性账本重放以重建 lot 与赎回明细。
ALTER TABLE fund_transaction
    ALTER COLUMN shares TYPE NUMERIC(19,2) USING round(shares, 2);
ALTER TABLE fund_lot
    ALTER COLUMN acquire_shares TYPE NUMERIC(19,2) USING round(acquire_shares, 2),
    ALTER COLUMN remaining_shares TYPE NUMERIC(19,2) USING round(remaining_shares, 2);
ALTER TABLE fund_lot_redemption
    ALTER COLUMN shares_consumed TYPE NUMERIC(19,2) USING round(shares_consumed, 2);

INSERT INTO accounting_rebuild_state(rebuild_key, status, details)
VALUES ('UNIT_NAV_V1', 'PENDING', 'Rebuild accounting with two-decimal shares')
ON CONFLICT (rebuild_key) DO UPDATE
SET status = 'PENDING', completed_at = NULL, details = EXCLUDED.details;
