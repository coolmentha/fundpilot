# 补充 QDII 估值

## Goal

让 QDII 基金在东方财富已提供当日估值时，能够及时进入 FundPilot 的行情缓存，并在行情工作台、基金列表和持仓收益视图中展示。

## Background

- 当前所有支持普通净值模型的基金统一调用东方财富 `fundgz`，见
  `backend/src/main/java/com/fundpilot/backend/market/service/FundEstimateService.java:37`。
- QDII 未被能力判断过滤，`FundMarketDataCapability.supportsStandardNav` 明确支持 QDII，见
  `backend/src/test/java/com/fundpilot/backend/market/service/support/FundMarketDataCapabilityTest.java:19`。
- 2026-07-16 23:52 实测六只 QDII 样例均从 `fundgz` 获得当日估值，`gztime=2026-07-16 22:29`；详细证据见
  `research/qdii-estimate-availability.md`。
- 当前 `MarketRealtimeRefreshJob` 只在 A 股 09:30-11:30、13:00-15:00 刷新全部基金估值，因此会错过北京时间晚间生成的 QDII
  当日估值。
- 当前 `DailyChangeResolver` 将北京时间 09:30 硬编码为所有基金的开盘点；`FundEntity` 没有 QDII 所属市场/时区字段，且 QDII
  可能投资美国、香港或多个市场，不能仅凭 `InvestmentTarget.QDII` 得到唯一交易时段。
- 当前盘前分支只返回今日涨跌 0；持仓市值和总盈亏仍会因为未使用最近已确认单位净值而返回空，不满足“交易前显示昨日净值”的新规则。
- 现有行情契约禁止用前一交易日估值、T-1/T-2 涨跌或旧缓存冒充今日估值，见 `.trellis/spec/backend/market-realtime-cache.md`。

## Requirements

- 所有普通净值基金复用同一套日内状态机，由每只基金估值源的当日 `gztime` 决定是否已进入估值阶段：
    - 当日交易开始前：展示上一期已确认净值，不使用估值。
    - 交易进行中：展示当日估值。
    - 交易结束后且当日净值尚未公布：继续展示最后一次有效估值。
    - 当日净值已公布：切换为已确认净值，不再展示估值。
- A 股基金与 QDII 的业务逻辑必须一致，差异仅来自各自当日估值出现的时间；不得复制两套涨跌或盈亏计算逻辑。
- “交易开始”可由公开估值源首次返回北京时间当日 `gztime` 来判定；未出现当日 `gztime` 时保持上一期已确认净值，避免新增无法可靠维护的
  QDII 市场分类字段。
- QDII 估值必须保留明确的数据日期、估值时间和估值状态。
- QDII 估值刷新必须覆盖公开源的晚间更新时间，且不得让指数、板块、资金等 A 股实时请求跟随扩展到晚间。
- 有当日有效估值时，复用现有 `AVAILABLE` 数据流，为今日涨跌、今日盈亏和总盈亏提供估算比例。
- 数据源无数据、数据过期或请求失败时，必须继续区分 `UNAVAILABLE`、`STALE`、`TIMEOUT` 和 `PARSE_ERROR`。
- 不得将已确认净值写成盘中估值，不得把估值数据写入 `fund_nav_history`。
- 优先复用现有缓存、状态和前端展示链路，不新增不必要的基础设施或依赖。

## Acceptance Criteria

- [x] 同一基金在交易前、交易中、交易后未公布净值、交易后已公布净值四个阶段返回符合要求的数据来源和估值标记。
- [x] 交易前今日涨跌为 0，持仓市值和总盈亏使用最近一期已确认单位净值计算，不再返回空。
- [x] A 股基金与 QDII 共用状态判定与收益计算，仅通过各自当日 `gztime` 的出现时间产生不同阶段结果。
- [x] QDII 在北京时间晚间获得当日 `fundgz` 数据后，缓存状态能从 `STALE/UNAVAILABLE` 更新为 `AVAILABLE`。
- [x] 普通基金现有估值行为不变。
- [x] QDII 估值过期、空响应和请求失败不会沿用旧缓存或冒充当日数据。
- [x] 基金列表、行情工作台和持仓收益继续通过现有字段展示，无重复计算链路。
- [x] 后端回归测试覆盖 QDII 成功与降级路径；现有前端契约测试、lint 和构建通过。

## Out Of Scope

- 修改已确认净值的入库与夜间确认流程。
- 为单只基金手工维护每日估值。
- 引入 Redis 或新的行情基础设施。
- 在已有 `fundgz` 数据可用的前提下新增代理估值算法或新估值数据源。
- 为 QDII 新增美国、香港、欧洲等人工市场归属字段和维护界面。
