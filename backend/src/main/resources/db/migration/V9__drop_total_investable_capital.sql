-- 平台转向行情工作台,仓位管理降级。
-- 移除 total_investable_capital 字段(总仓位≤80%硬约束 + 再平衡信号 + 计划仓位校验的分母)。
-- 连带移除的策略机制:再平衡信号、总仓位硬约束、计划仓位校验(详见 PRD)。
-- user_config 表只剩 watched_indices 字段(行情工作台用户关注指数)。
ALTER TABLE user_config DROP COLUMN IF EXISTS total_investable_capital;

-- 原 NOT NULL 约束已随列删除。watched_indices 可空(null=用默认指数列表)。
-- 若 user_config 表无任何业务字段会无法建行,但 watched_indices 允许 null,
-- 首次保存关注指数时会 INSERT 一行。getWatchedIndices() 在无行时返默认列表不抛错。
