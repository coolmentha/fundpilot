-- 回填历史 fund_lot:按 FIFO 假设(最早买入先被卖出消耗),只插入剩余未消耗的 lot。
-- 历史卖出不补记 fund_lot_redemption(R6.2 声明不回溯),FundEntity.costPerShare 不重算(R6.3)。
-- 用窗口函数算每只基金累计买入份额,与卖出总份额比较,算出每个 lot 的 remaining_shares。

WITH buy_lots AS (
    SELECT
        ft.fund_id,
        ft.id AS acquire_tx_id,
        ft.confirm_time AS acquire_date,
        ft.shares AS acquire_shares,
        ft.amount,
        -- 按 fund_id 分区,confirm_time 升序累加买入份额(含当前行)
        SUM(ft.shares) OVER (PARTITION BY ft.fund_id ORDER BY ft.confirm_time, ft.id) AS cum_buy_shares
    FROM fund_transaction ft
    WHERE ft.source IN ('INCREASE', 'TRANSFER_IN', 'INVEST')
      AND ft.status = 'CONFIRMED'
      AND ft.deleted_date IS NULL
      AND ft.shares IS NOT NULL
      AND ft.confirm_time IS NOT NULL
),
sell_totals AS (
    SELECT
        ft.fund_id,
        SUM(ft.shares) AS total_sold
    FROM fund_transaction ft
    WHERE ft.source IN ('DECREASE', 'TRANSFER_OUT')
      AND ft.status = 'CONFIRMED'
      AND ft.deleted_date IS NULL
      AND ft.shares IS NOT NULL
    GROUP BY ft.fund_id
),
lot_remaining AS (
    SELECT
        bl.fund_id,
        bl.acquire_tx_id,
        bl.acquire_date,
        bl.acquire_shares,
        bl.amount,
        CASE
            -- 之前累计买入已够覆盖卖出 → 本 lot 完全未消耗
            WHEN bl.cum_buy_shares - bl.acquire_shares >= COALESCE(st.total_sold, 0)
                THEN bl.acquire_shares
            -- 本 lot 跨越卖出边界 → 部分消耗
            WHEN bl.cum_buy_shares >= COALESCE(st.total_sold, 0)
                THEN bl.cum_buy_shares - COALESCE(st.total_sold, 0)
            -- 整个被消耗(remaining=0,后面 WHERE 过滤掉)
            ELSE 0
        END AS remaining_shares
    FROM buy_lots bl
    LEFT JOIN sell_totals st ON bl.fund_id = st.fund_id
)
INSERT INTO fund_lot (
    fund_id, acquire_tx_id, acquire_date, acquire_shares,
    remaining_shares, acquire_cost_per_share, version, created_date, updated_date
)
SELECT
    lr.fund_id,
    lr.acquire_tx_id,
    lr.acquire_date,
    lr.acquire_shares,
    lr.remaining_shares,
    CASE
        WHEN lr.amount IS NOT NULL AND lr.acquire_shares > 0
            THEN lr.amount / lr.acquire_shares
        ELSE 0
    END,
    0,
    now(),
    now()
FROM lot_remaining lr
WHERE lr.remaining_shares > 0;
