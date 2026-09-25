-- 清理无 Java 消费者的遗留表（V1 旧策略体系 + V11 旧定投计划，均已被
-- discipline→alerting 迁移与 V42 investment_plan 取代）。
-- 前置改写：AccountingRebuildService 对 fund_strategy 止盈状态机的重置已改为
-- 清空 alert_suggestion_state（V59 新表），本迁移须与该代码同版本发布。
-- 索引随表删除：uq_fund_strategy_effective / idx_fund_strategy_fund_status /
-- idx_fsa_fund_activated_at / idx_backtest_fund_strategy /
-- uq_fund_dca_plan_effective / idx_fund_dca_plan_fund。
-- fund_transaction.dca_plan_id 为裸列（无外键指向 fund_dca_plan），保留不动。
DROP TABLE IF EXISTS strategy_backtest;
DROP TABLE IF EXISTS fund_strategy_activation;
DROP TABLE IF EXISTS fund_strategy;
DROP TABLE IF EXISTS fund_dca_plan;
