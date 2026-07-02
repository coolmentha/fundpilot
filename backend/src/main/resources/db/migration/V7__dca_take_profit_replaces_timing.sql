-- V7: 定投移动止盈取代择时金字塔(ADR-0015)
-- 1) fund 表:planned_total_amount 退场,新增 dca_amount(每期定投金额,基金级用户输入)
-- 2) fund_strategy 表:删除择时金字塔字段,新增移动止盈参数(启动门槛/回撤分级4档/卖出比例/底仓保留/冷却期)
-- 3) signal_log 表:删除择时专属字段 trigger_tier / coefficient / hard_constraint_breaches
-- 4) fund_strategy 唯一约束与索引保持(EFFECTIVE 唯一不变)

-- fund 列变更
ALTER TABLE fund DROP COLUMN IF EXISTS planned_total_amount;
ALTER TABLE fund ADD COLUMN dca_amount NUMERIC(19, 8);

-- fund_strategy 列变更:先删择时专属字段,再加移动止盈参数
ALTER TABLE fund_strategy
    DROP COLUMN IF EXISTS tier1_drawdown,
    DROP COLUMN IF EXISTS tier2_drawdown,
    DROP COLUMN IF EXISTS tier3_drawdown,
    DROP COLUMN IF EXISTS tier4_drawdown,
    DROP COLUMN IF EXISTS tier1_ratio,
    DROP COLUMN IF EXISTS tier2_ratio,
    DROP COLUMN IF EXISTS tier3_ratio,
    DROP COLUMN IF EXISTS tier4_ratio,
    DROP COLUMN IF EXISTS weekly_cool_down_threshold,
    DROP COLUMN IF EXISTS stop_loss_pullback_percent,
    DROP COLUMN IF EXISTS tier1_added_at,
    DROP COLUMN IF EXISTS tier2_added_at,
    DROP COLUMN IF EXISTS tier3_added_at,
    DROP COLUMN IF EXISTS tier4_added_at;

ALTER TABLE fund_strategy
    ADD COLUMN activation_threshold NUMERIC(8, 6),
    ADD COLUMN pullback_tier_count INT,
    ADD COLUMN pullback_tier1_yield NUMERIC(8, 6),
    ADD COLUMN pullback_tier1_ratio NUMERIC(8, 6),
    ADD COLUMN pullback_tier2_yield NUMERIC(8, 6),
    ADD COLUMN pullback_tier2_ratio NUMERIC(8, 6),
    ADD COLUMN pullback_tier3_yield NUMERIC(8, 6),
    ADD COLUMN pullback_tier3_ratio NUMERIC(8, 6),
    ADD COLUMN pullback_tier4_yield NUMERIC(8, 6),
    ADD COLUMN pullback_tier4_ratio NUMERIC(8, 6),
    ADD COLUMN sell_ratio NUMERIC(8, 6),
    ADD COLUMN floor_ratio NUMERIC(8, 6),
    ADD COLUMN cooldown_days INT;

-- signal_log 列变更:删除择时专属字段
ALTER TABLE signal_log
    DROP COLUMN IF EXISTS trigger_tier,
    DROP COLUMN IF EXISTS coefficient,
    DROP COLUMN IF EXISTS hard_constraint_breaches;