# 行情与盈亏

## 业务目标与边界

行情模块为工作台展示、卖出纪律和交易确认提供不同时间尺度的数据。实时估值、日内行情、已确认净值和策略快照不能混为同一份数据。

## 数据层次

| 数据 | 存储 | 刷新/落库 | 主要消费者 |
| --- | --- | --- | --- |
| 指数、市场宽度、板块、非 QDII 基金估值与当日分时 | 后端内存副本 + Redis AOF | A 股交易时段每 30 秒；启动先恢复 Redis，再异步刷新 | 行情工作台、三态涨跌、基金详情 |
| `market_indicator_snapshot` | PostgreSQL | 14:30、14:40、14:50 三批 | 卖出信号生成 |
| `fund_nav_history` | PostgreSQL | 日间拉历史；当晚确认当日净值 | 交易确认、盈亏、年线、MACD、回撤 |
| `index_kline` | PostgreSQL | 行情指标拉取时顺带增量写入 | K 线图和量能指标 |

读接口只读取后端缓存或数据库，不在用户请求链路直接高频访问外部行情源。

## 实时行情缓存

- 请求只读进程内副本；每轮刷新后写穿 Redis，应用重启时先恢复持久化快照。
- Redis 暂时不可用时保留进程内缓存并记录警告，不阻断行情接口或外部数据刷新。
- 关注指数由 MarketData 的 `market_watched_index` 按用户拥有。读取实时指数时只返回当前用户选择的条目；后台刷新读取所有用户选择的去重并集。未配置时使用上证指数、沪深300、创业板指默认列表。
- `user_config.watched_indices` 仅保留给迁移审计，运行时不再读取或写入；月度定投预算仍由用户配置入口管理。
- 非 QDII 基金盘中估值优先同花顺分钟线，再按既定备用源降级；同一轮只拉取一次相同基准指数 K 线。QDII 不请求盘中估值。
- 指数 K 线首次拉完整窗口，已有缓存时只补最近窗口并覆盖重叠日期；基金净值源不支持日期参数，仍由落库层增量过滤。
- 指数、市场宽度、板块和资金数据刷新失败时保留各自旧缓存。
- 基金盘中估值是当天短时态数据，处理方式不同：单只基金本轮失败、空响应、旧日期或解析失败时，必须删除该基金旧估值并标记失败。
- 后续重新取得当天有效估值时，覆盖缓存并清除失败标记。
- 同花顺分钟线同时用于基金详情“今日分时”。仅缓存并展示北京时间当日、至少两个有效点的曲线；同花顺失败、过期、解析失败或后备源只返回单点时显示空态，不能用东方财富单点或历史数据补线。
- 非 QDII 的 `HOLDING` 和 `PENDING_HOLDING` 基金都进入估值刷新，观察池也能看到今日涨跌。
- 应用启动后的外部请求使用异步预热，不能阻塞 readiness。
- A 股交易时段刷新非 QDII 基金估值；晚间和跨夜不运行基金估值调度。每轮估值固定运行 25 秒，未完成的基金和批量分页在下轮从断点继续。

### 市场宽度

市场宽度固定汇总沪市 `1.000001`、深市 `0.399001`、北交所 `0.899050` 的上涨和下跌家数，与用户关注指数解耦。任一市场数据缺失时不发布部分合计，已有缓存保持不变。

### 市场量价关系

行情工作台用上证指数代表市场“价”，用东方财富返回的量比代表市场“量”，只展示一个总体判断，不按每个关注指数分别判断。量比与涨跌幅组合按以下口径分类：

- 量比 `>= 1.5` 为放量，`<= 0.5` 为缩量，`0.5 < 量比 < 1.5` 为量能平稳。
- 涨跌幅大于零为上涨，小于零为下跌，等于零为平盘。
- 放量上涨、缩量上涨、放量下跌、缩量下跌分别提示量能确认、动能不足、抛压扩大、方向偏弱的市场纪律；量能平稳和平盘只提供中性观察提醒，不生成基金买入、卖出或数量指令。

交易日 09:30-11:30、13:00-15:00 及午间休市展示“盘中暂估”；盘前、收盘和非交易日展示最近交易日收盘结论，并显示行情自身日期和时间。盘中行情超过约两分钟、行情日期不是应展示的最近交易日、涨跌幅/量比/行情时间缺失或解析非法、量比非正数时，展示“量能观察中”，不沿用过期结论冒充当前结论。第二次刷新失败时缓存可保留上一份快照，但查询只接受符合当前阶段、交易日和时效条件的快照。

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

若 22:59 前仍未公布或晚间请求失败，任务在次日 00:00-09:59 每 10 分钟通过交易日历定位上一交易日并补拉。补拉按目标日期检查幂等，已存在净值直接跳过；周末和节假日不能用前一自然日代替上一交易日。

单只基金失败不影响其他基金。估值接口的单位净值不能直接写入历史表替代完整净值序列。

## 今日涨跌三态

普通非 QDII 基金的 `今日涨跌` 是一个随业务时间切换数据源的概念：

| 状态 | 条件 | 值 |
| --- | --- | --- |
| 盘前 | 北京时间 09:30 前，且当日净值未落库 | 0，非估算 |
| 盘中/待公布 | 09:30 后，且当日净值未落库 | fundgz 估算涨跌幅，标记估算 |
| 盘后 | 当日净值已落库 | 当日累计净值 / 上一期累计净值 - 1 |

15:00 后到当日净值公布前仍属于待公布态，使用当天最后有效估值。没有有效估值时返回未知，不能使用 T-1 对 T-2 的昨日涨跌冒充今日值。

### QDII 确认日

QDII 净值可能滞后公布，因此今日收益的确认日按最新净值 `firstSeenAt` 的北京时间自然日判断，不要求 `navDate` 等于今天。

- `firstSeenAt` 是今天且已有两期净值时，按 `navDate` 倒序取最新一期和前一期计算今日涨跌与今日盈亏；同日发现多条净值时自然取 `navDate` 最大者。
- `firstSeenAt` 不是今天时，今日涨跌和今日盈亏为 0，避免次日重复结算同一段净值变化。
- 最近确认净值始终可用于持仓市值和总盈亏；QDII 非确认日不混用盘中估值。

## 盈亏口径

### 今日盈亏

`今日盈亏 = 昨日持仓市值 * 今日涨跌幅`

- 盘中基准使用最新已公布单位净值。
- 盘后基准使用上一期单位净值，涨跌幅使用累计净值复权比例。
- 无持仓时为 null。
- 组合今日盈亏只累加有当日数据的持仓，并同时返回覆盖数；页面必须明确提示部分口径。

### 当前持仓市值

- 盘后：`持仓份额 * 当日单位净值`。
- 盘中：`持仓份额 * 最新已公布单位净值 * (1 + 今日估算涨跌幅)`。
- 当日净值未落库且估值失败或缺失时，按最近确认净值展示持仓市值，并标记当日估值未覆盖。

### 总盈亏

`总盈亏 = 持仓份额 * (当前单位净值 - 成本单价)`

盘中使用按涨跌幅推算的单位净值，盘后使用已落库单位净值；当日估值不可用时使用最近确认单位净值。累计净值不能用于真实市值和成本。

这里的单只基金“总盈亏”是当前持仓的未实现盈亏。组合“累计收益”是另一口径，必须汇总有效交易产生的已实现收益与当前持仓未实现盈亏：清仓基金的当前未实现盈亏为 null，但其历史已实现收益继续计入组合累计收益；作废组合基金的全部数据则完全排除。

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

基金详情行情区默认展示“今日分时”，可切换到“K线 / 走势图”；今日分时默认显示相对 `baseNav` 的涨跌幅，也可切换为净值。两个视图都只读取后端缓存或数据库。

## 失败与降级

| 场景 | 行为 |
| --- | --- |
| 指数/市场宽度/板块刷新失败 | 保留对应旧缓存 |
| 基金估值失败或日期过期 | 删除该基金旧估值，标记失败 |
| 当日净值未落库且估值未知 | 今日涨跌/今日盈亏未知；当前市值和总盈亏使用最近确认净值 |
| QDII 最新净值不是今天首次发现 | 今日涨跌/今日盈亏为 0；当前市值和总盈亏使用最近确认净值 |
| 任一持仓今日盈亏未知 | 组合今日盈亏展示其余可用部分，并返回覆盖数 |
| 单只基金行情快照失败 | 跳过该基金并继续其他基金 |
| 外部数据源链全部失败 | `MARKET_DATA_ALL_SOURCES_FAILED` |

## 实现与验证入口

- 实现：[MarketRealtimeCache](../../backend/src/main/java/com/fundpilot/backend/market/service/MarketRealtimeCache.java)、[MarketDataFetchService](../../backend/src/main/java/com/fundpilot/backend/market/service/MarketDataFetchService.java)、[DailyNavConfirmService](../../backend/src/main/java/com/fundpilot/backend/market/service/DailyNavConfirmService.java)、[FundPnlService](../../backend/src/main/java/com/fundpilot/backend/fund/service/FundPnlService.java)、[DailyChangeResolver](../../backend/src/main/java/com/fundpilot/backend/fund/service/support/DailyChangeResolver.java)、[KlineService](../../backend/src/main/java/com/fundpilot/backend/market/service/KlineService.java)
- 测试：[MarketRealtimeCacheTest](../../backend/src/test/java/com/fundpilot/backend/market/service/MarketRealtimeCacheTest.java)、[MarketDataFetchServiceTest](../../backend/src/test/java/com/fundpilot/backend/market/service/MarketDataFetchServiceTest.java)、[DailyNavConfirmServiceTest](../../backend/src/test/java/com/fundpilot/backend/market/service/DailyNavConfirmServiceTest.java)、[FundPnlServiceTest](../../backend/src/test/java/com/fundpilot/backend/fund/service/FundPnlServiceTest.java)、[FundPnlServiceDateTest](../../backend/src/test/java/com/fundpilot/backend/fund/service/FundPnlServiceDateTest.java)
- 相关决策：[ADR-0002](../adr/0002-real-eastmoney-data-source.md)、[ADR-0006](../adr/0006-fund-nav-history-upsert-on-fetch.md)、[ADR-0008](../adr/0008-three-state-daily-change.md)、[ADR-0017](../adr/0017-csindex-replaces-blocked-push2his-for-index-kline.md)、[ADR-0019](../adr/0019-unit-nav-for-accounting.md)
