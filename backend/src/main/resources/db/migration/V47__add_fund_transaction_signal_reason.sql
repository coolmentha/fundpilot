-- 建议回应冗余来源原因：确认工作台展示卖出建议触发原因，避免查询时跨模块读取。
ALTER TABLE fund_transaction ADD COLUMN signal_reason VARCHAR(32);
