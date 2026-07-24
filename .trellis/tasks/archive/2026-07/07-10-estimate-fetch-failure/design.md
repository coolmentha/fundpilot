# 技术设计

## 数据流

`fundgz -> FundEstimateService -> MarketRealtimeCache -> FundPnlService -> FundView/PortfolioSummaryView -> React 页面`

净值事实链保持独立：`pingzhongdata/fundgz 确认 -> fund_nav_history -> FundPnlService`。

## 后端设计

### MarketRealtimeCache

- 保留现有 `estimateCache`，新增并发安全的失败基金代码集合。
- 每只基金刷新采用替换语义：
    - 成功且为北京时间当天：写入快照、清除失败标记。
    - 空、异常或旧日期：删除旧快照、写入失败标记。
- 使用注入的 `Clock` 判断北京时间当天，测试固定时间，避免依赖 CI 运行日期。
- 提供只读方法查询单基金是否拉取失败。

### FundPnlService

- `Pnl` 增加 `estimateFetchFailed`。
- 当日净值已确认时忽略估值失败状态，继续计算实际涨跌与盈亏。
- 当日净值未确认且今日涨跌未知时，不再用最新已公布净值计算“当前”持仓市值和总盈亏。
- `PortfolioSummary` 增加 `estimateFetchFailedCount`，只统计持仓基金。

### API View

- `FundView` 增加 `estimateFetchFailed`。
- `PortfolioSummaryView` 增加 `estimateFetchFailedCount`。
- `/api/market/funds/estimates` 继续只返回当前成功快照；失败状态由基金和组合 View 表达。

## 前端设计

- `PortfolioOverview`：全仓合计未知且失败数大于 0 时显示“估值拉取失败”，提示失败持仓基金数。
- `FundWatchlist`：单基金失败时不再回退 `dailyChangePct`，涨跌幅和当日收益显示失败文案。
- `FundsPage`、`FundDetailPage`：对应基金的今日涨跌/盈亏显示失败文案。
- 失败态优先于估值态和普通空值，防止 React Query 中不同轮询接口的旧值短暂回退。

## 风险与保护

- 风险：给 `MarketRealtimeCache` 增加 `Clock` 会影响直接构造它的单元测试。
    - 保护：统一固定北京时间测试时钟。
- 风险：API record 新字段影响所有构造点。
    - 保护：编译和 View 映射测试覆盖。
- 风险：估值失败时更多字段变为 null。
    - 保护：这是预期的显式未知状态，前端必须以失败文案接住，不展示部分或旧值。
