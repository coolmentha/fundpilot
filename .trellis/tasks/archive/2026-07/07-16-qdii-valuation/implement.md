# QDII 估值实施计划

## 1. 更新业务契约

- 更新 `CONTEXT.md` 的今日涨跌定义，写明由当日估值时间驱动、交易前使用最近确认净值。
- 更新 `docs/adr/0008-three-state-daily-change.md`，替换固定 09:30 的旧决策。
- 更新 `.trellis/spec/backend/market-realtime-cache.md` 的刷新窗口、状态矩阵和测试要求。

## 2. 调整统一状态机

- 先修改 `DailyChangeResolverTest`，覆盖实际净值、当日估值、旧日期/未尝试、空响应/失败四类状态。
- 修改 `DailyChangeResolver`，移除固定北京时间开盘常量，改为接收 `EstimateStatus`。
- 修改 `FundPnlService`，将估值状态传入 resolver，并在估值阶段开始前用最近确认单位净值计算持仓市值和总盈亏。
- 更新 `FundPnlServiceTest` / `FundPnlServiceDateTest`，覆盖交易前使用最近确认净值和失败状态不冒充当前值。

## 3. 扩展估值刷新窗口

- 将 `MarketRealtimeCache.refreshFundEstimates()` 改为公开的估值专用入口，`refreshAll()` 继续复用它。
- 在 `MarketRealtimeRefreshJob` 增加估值专用定时入口，复用同一个防重入保护；A 股交易时段避免重复刷新。
- 更新 `MarketRealtimeCacheTest` 和 `MarketRealtimeRefreshJobTest`，覆盖晚间、跨夜、中国节假日与 A 股时段去重。

## 4. 验证

- 运行定向测试：
    -
    `mvn -Dtest=DailyChangeResolverTest,FundPnlServiceTest,FundPnlServiceDateTest,MarketRealtimeCacheTest,MarketRealtimeRefreshJobTest test`
- 运行后端全量测试：`mvn test`
- 运行前端契约回归：`npm test -- --run`、`npm run lint`、`npm run build`。
- 检查 `git diff`，确认无 schema、依赖、REST 字段或无关格式化变更。

## 风险点

- `EstimateStatus.STALE` 与真正请求失败必须保持不同语义。
- 专用调度不得刷新指数、板块、市场宽度和资金。
- 中国交易日历不得阻断境外市场估值刷新。
- 当日净值落库必须始终覆盖估值和最近确认净值。
