# 行情与盈亏

## 业务目标与边界

行情模块为工作台展示、卖出纪律和交易确认提供不同时间尺度的数据。实时估值、日内行情、已确认净值和策略快照不能混为同一份数据。

## 数据层次

| 数据 | 存储 | 刷新/落库 | 主要消费者 |
| --- | --- | --- | --- |
| 指数、市场宽度、板块、基金估值 | 后端内存缓存 | 交易时段每 30 秒；启动后异步预热 | 行情工作台、三态涨跌 |
| `market_indicator_snapshot` | PostgreSQL | 14:30、14:40、14:50 三批 | 卖出信号生成 |
| `fund_nav_history` | PostgreSQL | 日间拉历史；当晚确认当日净值 | 交易确认、盈亏、年线、MACD、回撤 |
| `index_kline` | PostgreSQL | 行情指标拉取时顺带增量写入 | K 线图和量能指标 |

读接口只读取后端缓存或数据库，不在用户请求链路直接高频访问外部行情源。

## 实时行情缓存

- 指数、市场宽度、板块和资金数据刷新失败时保留各自旧缓存。
- 基金盘中估值是当天短时态数据，处理方式不同：单只基金本轮失败、空响应、旧日期或解析失败时，必须删除该基金旧估值并标记失败。
- 后续重新取得当天有效估值时，覆盖缓存并清除失败标记。
- `HOLDING` 和 `PENDING_HOLDING` 基金都进入估值刷新，观察池也能看到今日涨跌。
- 应用启动后的外部请求使用异步预热，不能阻塞 readiness。

### 市场宽度

市场宽度固定汇总沪市 `1.000001`、深市 `0.399001`、北交所 `0.899050` 的上涨和下跌家数，与用户关注指数解耦。任一市场数据缺失时不发布部分合计，已有缓存保持不变。

## 行情快照与信号时序

```text
14:30 fetchBatch(0)
14:40 fetchBatch(1)
14:50 fetchBatch(2) 完成后串行生成当日信号
```

三批覆盖所有未软删基金。每只基金使用独立事务拉取净值、计算年线/MACD/阶段高点、获取跟踪指数量能并写入当日快照。

单只基金失败时跳过该基金当日快照并继续其他基金；14:50 第三批整体抛出异常时不继续生成信号。

## 当晚净值确认

场外基金当日净值通常在收盘后公布。`DailyNavConfirmJob` 在工作日 20:00-22:59 每分钟轮询：

1. 已有当天净值的基金跳过。
2. 通过 fundgz 的基准净值日期判断当日净值是否已公布。
3. 已公布时从净值历史接口获取单位净值和累计净值并增量落库。
4. 新净值提交后发布更新事件，推动具备条件的 PENDING 交易继续确认。

单只基金失败不影响其他基金。估值接口的单位净值不能直接写入历史表替代完整净值序列。

## 今日涨跌三态

`今日涨跌` 是一个随业务时间切换数据源的概念：

| 状态 | 条件 | 值 |
| --- | --- | --- |
| 盘前 | 北京时间 09:30 前，且当日净值未落库 | 0，非估算 |
| 盘中/待公布 | 09:30 后，且当日净值未落库 | fundgz 估算涨跌幅，标记估算 |
| 盘后 | 当日净值已落库 | 当日累计净值 / 上一期累计净值 - 1 |

15:00 后到当日净值公布前仍属于待公布态，使用当天最后有效估值。没有有效估值时返回未知，不能使用 T-1 对 T-2 的昨日涨跌冒充今日值。

## 盈亏口径

### 今日盈亏

`今日盈亏 = 昨日持仓市值 * 今日涨跌幅`

- 盘中基准使用最新已公布单位净值。
- 盘后基准使用上一期单位净值，涨跌幅使用累计净值复权比例。
- 无持仓时为 null。
- 任一持仓今日盈亏未知时，组合今日盈亏合计也必须为未知，不能展示部分合计。

### 当前持仓市值

- 盘后：`持仓份额 * 当日单位净值`。
- 盘中：`持仓份额 * 最新已公布单位净值 * (1 + 今日估算涨跌幅)`。
- 当日净值未落库且估值失败或缺失时为未知，不能使用上一期净值冒充当前市值。

### 总盈亏

`总盈亏 = 持仓份额 * (当前单位净值 - 成本单价)`

盘中使用按涨跌幅推算的单位净值，盘后使用已落库单位净值。累计净值不能用于真实市值和成本。

### 两组独立统计

- 上涨/下跌基金：按今日涨跌幅正负统计。
- 盈利/亏损基金：按总盈亏正负统计。

一只基金可以今日上涨但整体亏损，两组统计不能互相替代。

## K 线

- ETF、指数和指数增强基金优先读取 `index_kline` 本地缓存。
- 日 K 由数据源提供，周 K/月 K 从日 K 聚合。
- 本地缓存为空时才允许实时数据源降级获取。
- 主动或混合基金、或指数 K 线最终不可用时，降级为净值面积图。
- 指数日 K 数据源链优先中证指数公司，再按能力回退其他数据源。

## 失败与降级

| 场景 | 行为 |
| --- | --- |
| 指数/市场宽度/板块刷新失败 | 保留对应旧缓存 |
| 基金估值失败或日期过期 | 删除该基金旧估值，标记失败 |
| 当日净值未落库且估值未知 | 今日涨跌、当前市值和总盈亏返回未知 |
| 任一持仓今日盈亏未知 | 组合今日盈亏返回未知 |
| 单只基金行情快照失败 | 跳过该基金并继续其他基金 |
| 外部数据源链全部失败 | `MARKET_DATA_ALL_SOURCES_FAILED` |

## 实现与验证入口

- 实现：[MarketRealtimeCache](../../backend/src/main/java/com/fundpilot/backend/market/service/MarketRealtimeCache.java)、[MarketDataFetchService](../../backend/src/main/java/com/fundpilot/backend/market/service/MarketDataFetchService.java)、[DailyNavConfirmService](../../backend/src/main/java/com/fundpilot/backend/market/service/DailyNavConfirmService.java)、[FundPnlService](../../backend/src/main/java/com/fundpilot/backend/fund/service/FundPnlService.java)、[DailyChangeResolver](../../backend/src/main/java/com/fundpilot/backend/fund/service/support/DailyChangeResolver.java)、[KlineService](../../backend/src/main/java/com/fundpilot/backend/market/service/KlineService.java)
- 测试：[MarketRealtimeCacheTest](../../backend/src/test/java/com/fundpilot/backend/market/service/MarketRealtimeCacheTest.java)、[MarketDataFetchServiceTest](../../backend/src/test/java/com/fundpilot/backend/market/service/MarketDataFetchServiceTest.java)、[DailyNavConfirmServiceTest](../../backend/src/test/java/com/fundpilot/backend/market/service/DailyNavConfirmServiceTest.java)、[FundPnlServiceTest](../../backend/src/test/java/com/fundpilot/backend/fund/service/FundPnlServiceTest.java)、[FundPnlServiceDateTest](../../backend/src/test/java/com/fundpilot/backend/fund/service/FundPnlServiceDateTest.java)
- 相关决策：[ADR-0002](../adr/0002-real-eastmoney-data-source.md)、[ADR-0006](../adr/0006-fund-nav-history-upsert-on-fetch.md)、[ADR-0008](../adr/0008-three-state-daily-change.md)、[ADR-0017](../adr/0017-csindex-replaces-blocked-push2his-for-index-kline.md)、[ADR-0019](../adr/0019-unit-nav-for-accounting.md)
