-- 金字塔加仓机制退场:删 fund.planned_total_amount 与 fund_strategy 的金字塔档位列。
-- 移动止盈 stop_loss_pullback_percent 保留(解耦为独立"按回落分档减仓"规则的阈值)。
-- 历史 SignalLog 数据不受影响(triggerTier/coefficient 列在 signal_log 表,未动)。

ALTER TABLE fund DROP COLUMN IF EXISTS planned_total_amount;

ALTER TABLE fund_strategy
    DROP COLUMN IF EXISTS tier1_drawdown,
    DROP COLUMN IF EXISTS tier2_drawdown,
    DROP COLUMN IF EXISTS tier3_drawdown,
    DROP COLUMN IF EXISTS tier4_drawdown,
    DROP COLUMN IF EXISTS tier1_ratio,
    DROP COLUMN IF EXISTS tier2_ratio,
    DROP COLUMN IF EXISTS tier3_ratio,
    DROP COLUMN IF EXISTS tier4_ratio,
    DROP COLUMN IF EXISTS tier1_added_at,
    DROP COLUMN IF EXISTS tier2_added_at,
    DROP COLUMN IF EXISTS tier3_added_at,
    DROP COLUMN IF EXISTS tier4_added_at,
    DROP COLUMN IF EXISTS weekly_cool_down_threshold;
