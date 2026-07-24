# 行情数据可靠性与请求性能设计

## Architecture

修复保持现有三层边界：Controller 只路由，Service 负责编排，Client/Parser 负责外部协议。核心变化集中在四个边界：

1. 外部调用边界：所有手工 Feign client 使用显式 `connect=1s/read=3s`，禁用 Feign 自动重试，并移除 K 线的无预算手动重试。
2. 数据解析边界：从已知 JS 变量赋值中用括号匹配提取 JSON 数组，再交给 Jackson；不执行远端 JavaScript。
3. 缓存状态边界：估值缓存同时保存数据和状态，状态不再由一个失败 Set 表达。
4. 任务边界：外部请求在事务外执行，持久化阶段使用短事务；调度池允许独立任务并行，但同一高频任务禁止重入。

## External Call Contract

### Timeout Budget

- Feign connect timeout：1 秒。
- Feign read timeout：3 秒。
- 东方财富共享限流器单次等待最多 1 秒，令牌不可用时快速失败并进入降级。
- 净值路径为东方财富后回退同花顺；同花顺净值内部需要单位/累计两次请求。K 线路径为中证指数公司、东方财富、同花顺三个真实来源。最坏调用预算均控制在前端
  15 秒内。
- 删除 `EastmoneyMarketDataSource.fetchKlineWithRetry` 的手动重试。
- Nginx 显式设置 `proxy_connect_timeout 3s`、`proxy_read_timeout 15s`、`proxy_send_timeout 15s`，前端仍以 15 秒为最终取消边界。

### Source Chain

- 将占位 `ThsClient/ThsJsParser` 重构为真实同花顺复合数据源，公开接口与验证证据见 `research/ths-public-market-data.md`。
- 同花顺净值使用 `fund.10jqka.com.cn/{code}/json/jsondwjz.json` 与 `jsonljjz.json`，按日期关联单位/累计净值。
- 同花顺基金字典使用 `fund.10jqka.com.cn/data/Net/info/..._jsonp_g.html`，解析 `code/name/typename`；不使用证书主机名不匹配的
  `fund.ijijin.cn`。
- 同花顺指数 K 线使用 `d.10jqka.com.cn/v6/line/{internalCode}/01/last.js`，解析最近 140 根日线；周/月周期在本地聚合。
- 指数内部代码按市场规则转换：`000001.SH -> hs_1A0001`、其他 `000xxx.SH -> hs_1Bxxxx`、`399xxx.SZ -> hs_399xxx`
  、CSI/主题指数 -> `120_{code}`。
- Collection 或 `IndexKline` 空结果记为 `empty`，继续尝试下一个真实来源；全链无数据时抛现有
  `MARKET_DATA_ALL_SOURCES_FAILED`。
- `UnsupportedOperationException` 只表示该 source 不支持当前 operation，不计为真实故障。

## Structured JS Parsing

新增解析辅助方法，按变量名定位赋值起点，并使用字符串感知的括号匹配找到完整 JSON 数组。数组交给 Jackson 解析：

- `Data_netWorthTrend`：按时间戳读取单位净值。
- `Data_ACWorthTrend`：按时间戳建立累计净值 Map，再与单位净值按日期关联，避免依赖数组长度和索引完全一致。
- `r`：解析基金字典二维数组。

没有目标变量、没有可关联净值或结构不兼容时返回空结果，由调用边界记录 `empty`。东方财富解析路径不再创建 Graal
Context；GraalVM 依赖仅保留给新浪 KLC 交易日历解码器。

## NAV Confirmation Flow

晚间任务改为每 5 分钟执行一次：

```
读取基金 ID/code/localLatestDate
    -> 事务外拉 pingzhongdata
    -> 过滤 navDate > localLatestDate
    -> 无新增：记录 empty/no_change，结束
    -> 有新增：短事务内重新读取最新日期并幂等 saveAll
```

- 不再调用 fundgz 作为净值发布门卫。
- 不要求新净值日期等于今天，FOF/QDII 可按实际滞后日期增量入库。
- 普通基金当日净值入库后，现有三态涨跌逻辑自动切换到实际值。
- 货币基金/REIT 无兼容净值时保持无新增，不记录为 transport failure。
- 新增基金数据能力策略：优先读取 `fund_dict.rawName`，再用基金名称兜底识别货币基金/REIT；这些类型在进入任何普通净值 source
  前返回中性 unsupported，防止同花顺兼容形态被误当成普通净值。

`MarketDataFetchService` 的日常拉取也复用“只写晚于本地最新日期”的增量方式，避免每次读取全部已落库日期。

## Estimate State Contract

新增内部/API 兼容枚举：

- `NOT_ATTEMPTED`：进程启动后尚未完成首次尝试。
- `AVAILABLE`：存在北京时间当天有效估值。
- `UNAVAILABLE`：空响应、缺关键字段或当前产品不提供估值；前端显示“暂无估值”。
- `STALE`：响应存在但估值时间不是今天；前端中性显示。
- `TIMEOUT`：连接/读取超时；前端显示“估值拉取失败”。
- `PARSE_ERROR`：响应格式异常；前端显示“估值拉取失败”。

`FundView` 新增 `estimateStatus`，保留现有 `estimateFetchFailed` 字段并仅在 `TIMEOUT/PARSE_ERROR` 时为 true，保持兼容。
`FundEstimateView` 继续只返回可用快照。

## Scheduling And Transactions

- 配置 `spring.task.scheduling.pool.size=2`，避免一个慢任务阻塞所有定时任务。
- `MarketRealtimeRefreshJob` 使用 `AtomicBoolean` 防止同一任务重入；跳过时记录指标/日志。
- 移除 `MarketRealtimeCache` 刷新方法和 `KlineService` 上覆盖外部调用的长只读事务。
- `DailyNavConfirmService` 仅将最终增量写入放入 `RequiresNewTransactionExecutor`。
- 本次优先处理行情高频/晚间链路；初始持仓创建的同步净值获取保持原子业务约束，不在本任务拆分。

## Metrics

新增 `MarketDataMetrics`，使用 Micrometer 记录：

- `market_data_external_duration_seconds{source,operation,result}`
- `market_data_external_calls_total{source,operation,result}`

结果枚举固定为 `success/timeout/empty/unsupported/parse_error/failure`，避免高基数。`JobMetricsAspect` 为 Timer 开启
percentile histogram，使现有 Grafana P99 查询产生 `_bucket` 数据。

## Compatibility

- 不修改现有 URL、请求参数和既有字段含义。
- `FundView.estimateStatus` 是新增字段；旧前端忽略它仍可运行。
- 数据库 schema 不变，不需要迁移。
- 东方财富解析器不再依赖 GraalVM 执行；新浪交易日历仍使用现有 GraalVM 解码器，回滚只需恢复原镜像。

## Risks And Rollback

- 结构提取器必须覆盖 BOM、空格、分号、字符串转义和嵌套数组；通过真实脱敏样本锁定。
- 1s/3s 超时可能增加快速失败次数，但不会再让一个请求占用线程一分钟；缓存读取路径继续提供旧值或中性状态。
- 调度池改为 2 后不同任务可并行，必须依靠每任务重入保护和数据库唯一约束保证幂等。
- 回滚方式：回退应用镜像；无 schema 变化，无数据迁移回滚。
