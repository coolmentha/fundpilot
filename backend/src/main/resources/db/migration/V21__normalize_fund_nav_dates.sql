-- 东方财富用北京时间零点的 epoch 毫秒表示净值日，直接转 Instant 会落成前一日 16:00Z。
-- fund_nav_history 的日期契约是“北京时间自然日对应的 UTC 00:00 标签”，统一修正存量数据。
-- 先移除索引，避免连续日期从 16:00Z 平移到次日 00:00Z 时产生瞬时唯一键冲突。
DROP INDEX IF EXISTS uq_fund_nav_history_daily;

UPDATE fund_nav_history
SET nav_date = date_trunc('day', nav_date AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'UTC'
WHERE nav_date IS NOT NULL
  AND nav_date <> date_trunc('day', nav_date AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'UTC';

-- 历史上 UTC 零点行与东方财富 16:00Z 行可能代表同一个北京时间自然日。
-- 保留净值字段更完整、更新时间更新、id 更大的活动行，其余行按实体约定软删除。
WITH ranked AS (
    SELECT id,
           row_number() OVER (
               PARTITION BY fund_id, nav_date
               ORDER BY ((nav IS NOT NULL)::int + (accumulated_nav IS NOT NULL)::int) DESC,
                        updated_date DESC NULLS LAST,
                        id DESC
           ) AS rn
    FROM fund_nav_history
    WHERE fund_id IS NOT NULL
      AND nav_date IS NOT NULL
      AND deleted_date IS NULL
)
UPDATE fund_nav_history history
SET deleted_date = now(), updated_date = now()
FROM ranked
WHERE history.id = ranked.id
  AND ranked.rn > 1;

CREATE UNIQUE INDEX uq_fund_nav_history_daily
    ON fund_nav_history (fund_id, ((nav_date AT TIME ZONE 'UTC')::date))
    WHERE deleted_date IS NULL;
