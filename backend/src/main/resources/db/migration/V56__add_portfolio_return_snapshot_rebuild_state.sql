-- 组合收益快照的一次性重算状态。累计收益口径改为「已实现 + 未实现」后，
-- 存量历史点仍是旧的现金流口径，需要按新口径重放回填。空库无需重算。
CREATE TABLE insights_rebuild_state
(
    rebuild_key  VARCHAR(64) PRIMARY KEY,
    status       VARCHAR(16) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    details      TEXT
);

INSERT INTO insights_rebuild_state(rebuild_key, status, details)
SELECT 'RETURN_TOTAL_V2', 'PENDING', 'Recapture snapshots as realized plus unrealized'
WHERE EXISTS (
    SELECT 1 FROM portfolio_return_snapshot
    WHERE deleted_date IS NULL
);
