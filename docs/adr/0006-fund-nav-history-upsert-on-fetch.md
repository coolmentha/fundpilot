# fund_nav_history 落库：在行情拉取时增量 upsert 净值序列

`fund_nav_history` 表存储基金每日净值（单位净值 + 累计净值），是盈亏/涨跌计算（`FundPnlService`）、
净值确认（`NavConfirmService`）、信号生成（`SignalGenerationService` 的峰值/单周跌幅）等多个领域的读数据源。
但该表在生产代码中长期**无写入点**——`MarketDataFetchService.fetchOne` 调 `pingzhongdata.js` 拉全量净值序列，
仅用于计算 `market_indicator_snapshot` 指标（年线/MACD/量能），未落库；其余服务只读。导致 `FundPnlService`
能算涨跌但生产表为空、所有基金涨跌为 null。本期（issue #23）补建落库逻辑。

## Considered Options

- **A. 在 fetchOne 拉取后增量 upsert（已采纳）**：`MarketDataFetchService.fetchOne` 拉到 pingzhongdata
  净值序列后，调 `upsertNavHistory` 增量写入 `fund_nav_history`。按 fundId+navDate **应用层去重**——
  查已落库的 navDate 集合，filter 出新的 `saveAll`，避免违反 `uq_fund_nav_history_daily` 部分唯一索引
  （每基金每交易日唯一）。首次全量落库（数百条），后续每日增量补最新一期。
- **B. 独立 NavHistorySyncJob 单独拉取落库**：新建定时任务专拉净值序列落库，与行情指标拉取分离。
- **C. DB ON CONFLICT upsert（native SQL）**：用 PostgreSQL `INSERT ... ON CONFLICT (fund_id, nav_date) DO UPDATE`
  原生 upsert，数据库层去重。

## Consequences

选 A 的核心理由：**fetchOne 已拉全量净值序列，顺便落库零额外外部请求；应用层去重比 native upsert 简单**。

1. 零额外外部请求——`fetchOne` 本就为算指标拉了全量净值（含历史），落库只是把已拉到的数据多写一张表，
   不新增任何 pingzhongdata 调用（稀缺的东方财富配额，ADR-0002）。
2. 应用层去重足够——单只基金每日最多新增 1 条，查询已有 navDate 集合 + filter 是 O(n) 内存操作，
   量极小。`saveAll` 只插新行，不触发唯一索引冲突。
3. 范围同步扩大——`fetchBatch` 遍历从 `findEffectiveFundIds`（仅 EFFECTIVE 策略基金）改为
   `fundRepository.findAll`（所有未软删基金，`@SQLRestriction` 自动过滤），让未建仓观察池基金也落净值历史，
   支撑 `FundPnlService` 算其今日涨跌（story 21）。

不选 B：`fetchOne` 已有数据，独立 job 再拉一次是重复外部请求。
不选 C：JPA `saveAll` 不支持 ON CONFLICT，需写 native SQL 绕过 JPA，复杂度高于应用层去重；且去重量小，
应用层 filter 完全够用。若未来基金数增长导致全量落库慢，可再考虑增量查最新 navDate 只拉差量。

## 与现有架构的关系

落库点放在 `fetchOne` 而非 `fetchBatch`，因 `fetchOne` 已持有 fund + navHistory 两个对象，落库逻辑就近。
`MarketDataFetchService` 的职责从"拉指标落 `market_indicator_snapshot`"扩展为"拉净值序列落 `fund_nav_history`
+ 拉指标落 `market_indicator_snapshot`"，二者共享同一次 pingzhongdata 请求。
`FundStrategyRepository.findEffectiveFundIds` 不再被 `MarketDataFetchService` 使用（改为 `findAll`），
但 `SignalGenerationService` 仍用它确定信号生成范围（有策略才生成信号）——两处范围解耦：
**行情拉取覆盖全部基金，信号生成只覆盖有策略的基金**。

## 去重实现要点

`FundNavHistoryRepository.findNavDatesByFundEntity_Id` 返回已落库 navDate 集合；
`upsertNavHistory` 用 `HashSet` contains 过滤已有日期，只 `saveAll` 新 snapshot。
依赖 `uq_fund_nav_history_daily` 部分唯一索引（`fund_id, (nav_date AT TIME ZONE 'UTC')::date WHERE deleted_date IS NULL`，
`V1__init_schema.sql`）兜底——应用层去重失败（并发等边缘情况）时数据库约束拦截。
