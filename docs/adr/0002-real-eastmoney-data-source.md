# 东方财富/天天基金接口作为真实行情数据源，替代"半自动灌入"

本期就将东方财富/天天基金公开接口作为真实行情数据源接入，不走文档最初计划的"半自动灌入"占位模式。

## 背景

原设计（`backend/docs/落地讨论-策略实现.md §3.2` / `推荐架构方案.md §0`）将市场指标（年线、MACD、成交量、60日新高）标记为"
本期不接行情源，由定时任务在 14:50 通过 `MarketIndicatorProvider` 拉取（本期可半自动灌入）"。两条独立的写入路径：净值历史
`fund_nav_history` 人工录入为主，技术指标不实现。

实际调研发现：

1. **净值数据**（`pingzhongdata.js`）可以真实拉取（`Data_netWorthTrend` + `Data_ACWorthTrend`），每日一次，无需人工录入。
2. **技术指标**：MACD 和年线基于净值序列自行计算，无需额外接口；量能（成交量）需走东方财富 K 线接口（`push2his.eastmoney.com`
   ），但有基金类型限制（ETF/指数基金可对应指数 K 线，主动基金无成交量数据源）。
3. **基金字典**（`fundcode_search.js`）含全量约 2 万只基金的类型/名称，可用于 `fundSubType` 和 `benchmarkIndexCode` 自动识别。

## 决策

- 三条数据线（净值历史 + 基金字典 + 指数 K 线）本期全部真实接入。
- 主动基金的量能指标降级为"单周跌幅 > weeklyCoolDownThreshold" 替代，不强制要求 `benchmarkIndexCode`。
- `MarketIndicatorProvider` 按 `fundSubType` 分派不同实现（ETF/指数走指数 K 线，主动走净值跌幅）。

## Consequences

- 工作量显著增加（约翻倍）：从"预留空接口"变为"实现两个 Provider + 限流 + 自动基金识别 + 表级缓存"。
- 时序约束：14:50 信号生成前需完成所有数据抓取和计算；抓取失败有降级兜底（指标不全 → 当天
  `signalType=NONE, reason=INSUFFICIENT_MARKET_DATA`）。
- 限流约束：东方财富对 IP 有限速（约每秒 2-3 次），需用 `Semaphore` 或 `Bucket4j` 做节流；加
  `Referer: https://fund.eastmoney.com/` 请求头避免被反爬。

## 原"半自动灌入"定位

原设计中的"半自动灌入"路线已废弃。`MarketIndicatorProvider` 接口保留，但不会再有半自动灌入的
`ManualMarketIndicatorProvider` 实现。