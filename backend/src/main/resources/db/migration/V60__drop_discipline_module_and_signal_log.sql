-- C-5 / C-6（v0.14.0）：删除纪律（discipline）模块的表，以及账务链路上的纪律外键列。
--
-- ★ 不可逆操作：本脚本只删不建，执行后无法通过迁移回滚。执行前必须先备份数据：
--   pg_dump -Fc -f fundpilot-before-v60.dump "<DATABASE_URL>"
--   需要能恢复到删除前状态时，用 pg_restore 还原该 dump；仅做 schema-only 备份不足以回滚数据。
--
-- 前置核查（C-6 的顺序硬约束）：生产实测
--   select count(*) from fund_transaction where signal_log_id is not null  →  0 行，
-- 因此直接删列不会改变 AccountingRebuildService 的「建仓 vs 信号加仓」判定结果
-- （该判定已改写为 t.source='INCREASE' and t.dca_plan_id is null）。
-- 保留 fund_product.default_discipline_category：它是 D5 之后的收益分类来源，由
-- productcatalog 的目录同步链路（ProductCatalogSynchronizationWriter）持续写入。

-- 1. 删 fund_transaction 上的纪律外键列。
--    先删约束与索引，再删列，避免遗留悬空对象。
ALTER TABLE fund_transaction DROP CONSTRAINT IF EXISTS fk_ft_signal_log;
DROP INDEX IF EXISTS idx_ft_signal_log;
ALTER TABLE fund_transaction DROP COLUMN IF EXISTS signal_log_id;

ALTER TABLE fund_transaction DROP COLUMN IF EXISTS signal_reason;

ALTER TABLE fund_transaction DROP CONSTRAINT IF EXISTS fk_fund_transaction_discipline_advice;
DROP INDEX IF EXISTS uq_fund_transaction_discipline_advice;
ALTER TABLE fund_transaction DROP COLUMN IF EXISTS discipline_advice_id;

-- 2. 删纪律三表。discipline_advice 通过 discipline_strategy_id 依赖 discipline_strategy，
--    故先删依赖方 discipline_advice，再删被依赖的 discipline_strategy。
DROP TABLE IF EXISTS discipline_advice;
DROP TABLE IF EXISTS discipline_strategy;
DROP TABLE IF EXISTS discipline_classification;

-- 3. 删 signal_log。其唯一的入向外键 fk_ft_signal_log 已随上面的删列移除，
--    且 Java 侧已无实体与运行时读写方（仅历史迁移脚本引用）。
DROP TABLE IF EXISTS signal_log;
