-- v0.14.0 C 段上线前的只读核查（C-5 / C-6 / D5 依据）
-- 执行方式：docker exec -i fundpilot-db psql -U fundpilot -d fundpilot -f -
\pset border 0
\echo == C-6 ① signal_log_id 非空行数（决定是否需要替代标记列）==
select count(*) as ft_signal_log_id_not_null from fund_transaction where signal_log_id is not null;
\echo == C-5 待删列的非空行数 ==
select count(*) as ft_signal_reason_not_null from fund_transaction where signal_reason is not null;
select count(*) as ft_discipline_advice_id_not_null from fund_transaction where discipline_advice_id is not null;
\echo == C-5 待删表行数 ==
select count(*) as discipline_advice_rows from discipline_advice;
select count(*) as discipline_strategy_rows from discipline_strategy;
select count(*) as discipline_classification_rows from discipline_classification;
select count(*) as signal_log_rows from signal_log;
\echo == D5 分类来源：目录值分布（保留列）==
select coalesce(default_discipline_category, '<null>') as directory_category, count(*)
  from fund_product group by 1 order by 2 desc;
\echo == 迁移前 Flyway 版本 ==
select version from flyway_schema_history where success order by installed_rank desc limit 1;