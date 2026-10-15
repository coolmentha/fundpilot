-- 退役旧指标链路（全量审计确认级发现）。
-- market_indicator_snapshot：V3 建立的每日指标快照缓存（年线/周MACD/量能/单周跌幅/60日新高），
--   旧纪律策略信号的输入。策略模块退役（V60）后仅剩 FundMarketIndicatorController 读侧端点，
--   前端零调用；写入侧 MarketIndicatorRefreshCommandHandler 同步移除快照写入，
--   保留净值发布、指数 K 线供血与 PE 估值刷新（低估值定投策略依赖）。
DROP TABLE IF EXISTS market_indicator_snapshot;
