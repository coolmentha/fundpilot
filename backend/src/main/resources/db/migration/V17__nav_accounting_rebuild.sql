-- 单位净值账目口径的一次性重建状态。仅存量存在已确认交易时创建待处理任务，空库无需重建。
CREATE TABLE accounting_rebuild_state (
    rebuild_key VARCHAR(64) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    details TEXT
);

INSERT INTO accounting_rebuild_state(rebuild_key, status, details)
SELECT 'UNIT_NAV_V1', 'PENDING', 'Rebuild confirmed transactions with unit NAV semantics'
WHERE EXISTS (
    SELECT 1 FROM fund_transaction
    WHERE status = 'CONFIRMED' AND deleted_date IS NULL
);

-- 建唯一索引前消解同计划同一北京时间自然日的存量重复。
-- CONFIRMED > CANCELLED > PENDING，同状态保留最小 id。
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY dca_plan_id, ((trade_date AT TIME ZONE 'Asia/Shanghai')::date)
               ORDER BY CASE status
                            WHEN 'CONFIRMED' THEN 1
                            WHEN 'CANCELLED' THEN 2
                            ELSE 3
                        END,
                        id
           ) AS rn
    FROM fund_transaction
    WHERE dca_plan_id IS NOT NULL
      AND trade_date IS NOT NULL
      AND deleted_date IS NULL
)
UPDATE fund_transaction ft
SET deleted_date = now(), updated_date = now()
FROM ranked r
WHERE ft.id = r.id AND r.rn > 1;

CREATE UNIQUE INDEX uq_fund_transaction_dca_beijing_day
    ON fund_transaction (
        dca_plan_id,
        ((trade_date AT TIME ZONE 'Asia/Shanghai')::date)
    )
    WHERE dca_plan_id IS NOT NULL
      AND trade_date IS NOT NULL
      AND deleted_date IS NULL;
