-- 清理无 Java 消费者的遗留表与死列（全量审计确认级发现）。
-- user_config：V3 单用户账户配置，watched_indices 与月度定投预算已分别被
--   market_watched_index（V39）与 investment_plan_budget（V42）取代，main 中无任何代码引用；
--   仅存在自向外键 fk_user_config_owner（→ site_user），无他表引用，索引随表删除。
-- fund_product_migration_conflict：V36 一次性迁移对账台账，对账已完成，无代码引用。
-- fund.operation_mode / investment_philosophy：V1 遗留列，main/test 中无任何读写。
DROP TABLE IF EXISTS user_config;
DROP TABLE IF EXISTS fund_product_migration_conflict;
ALTER TABLE fund DROP COLUMN IF EXISTS operation_mode;
ALTER TABLE fund DROP COLUMN IF EXISTS investment_philosophy;
