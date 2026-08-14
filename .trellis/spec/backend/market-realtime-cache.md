# Realtime Market Cache Layer

> 行情工作台实时数据缓存层契约。本任务(task 07-04-market-dashboard-pivot)引入,
> 解决「前端 5-10s 高频轮询 vs 东方财富共享限流」矛盾。

---

## Scope / Trigger

- 触发:新增行情工作台,前端高频轮询实时行情数据(指数/板块/资金/基金估值)
- 强制 code-spec:跨层契约(前端轮询频率 ↔ 后端缓存刷新频率 ↔ 东方财富限流)

---

## Signatures

### 后端 REST 接口(`MarketRealtimeController`)

```
GET /api/market/indices/realtime     → List<IndexRealtimeView>
GET /api/market/breadth              → MarketBreadthView
GET /api/market/funds/estimates?codes=xxx,yyy → Map<String, FundEstimateView>
GET /api/market/sectors              → List<SectorView>
GET /api/market/money-flow           → MoneyFlowView
GET /api/market/status               → MarketStatusView
GET /api/funds/{fundId}/kline?period=daily|weekly|monthly → KlineView
```

### 缓存服务(`MarketRealtimeCache`)

```java
public List<IndexRealtimeSnapshot> getIndices();        // 读进程内副本，刷新后写穿 Redis
public MarketBreadthSnapshot getBreadth();              // 沪深京股票涨跌、涨停、跌停家数
public List<SectorSnapshot> getSectors();
public MoneyFlowSnapshot getMoneyFlow();
public Instant getMarketUpdatedAt();                      // 指数/宽度/行业中最旧的成功刷新时间
public Map<String, FundEstimateSnapshot> getEstimates(List<String> codes); // 只读缓存,不拉外部接口
public EstimateStatus getEstimateStatus(String code);
public Map<String, EstimateStatus> getEstimateStatuses(List<String> codes);
public void refreshAll();                               // 全量刷新(含估值)
public void refreshRealtimeWithoutEstimates();          // 仅刷新指数/市场宽度/板块/资金
public void refreshFundEstimates();                     // 刷新非 QDII 平台基金估值
@Async public void onApplicationReady();                // 启动完成后后台预热实时行情
@Async public void warmFundEstimatesAfterReady();       // 启动完成后后台预热非 QDII 基金估值
```

### 定时任务(`MarketRealtimeRefreshJob`)

```java
@Scheduled(cron = "*/30 * 9-14 * * MON-FRI", zone = "Asia/Shanghai")
public void refreshRealtime();  // A 股交易日与交易时段内刷新指数/市场宽度/板块/资金

@Scheduled(cron = "*/30 * 9-14 * * MON-FRI", zone = "Asia/Shanghai")
public void refreshFundEstimates(); // A 股交易时段刷新非 QDII 平台基金
```

`FundView` 新增 `estimateStatus: NOT_ATTEMPTED|AVAILABLE|UNAVAILABLE|STALE|TIMEOUT|PARSE_ERROR`；
兼容字段 `estimateFetchFailed` 仅在 `TIMEOUT/PARSE_ERROR` 时为 true。

`fund.investment_target` 是 QDII 收益分支的持久化判定字段。`V26__backfill_qdii_investment_target.sql`
仅把名称含 `QDII` 且该字段为空的存量基金回填为 `QDII`；`FundService.create/update` 对后续空分类执行相同识别。

---

## Contracts

### 数据刷新频率契约

| 数据类型 | 后端刷新 | 前端轮询 | 理由 |
|---------|---------|---------|------|
| 指数实时 | 30s | 5s | 单请求批量,快 |
| 行业涨跌 | 30s | 30s | 单请求，`pz=100` 覆盖当前完整行业范围 |
| 行业主力资金 | 30s | 30s | 随板块快照批量返回 |
| 市场状态/快照时间 | 随核心行情刷新 | 30s | 只读缓存与交易日历 |
| 基金估值 | **30s** | 10s | 仅覆盖非 QDII 基金的 A 股交易时段；失败立即失效该基金旧估值 |

**关键不变量**:前端轮询频率 > 后端刷新频率。前端读内存零外部请求,
刷新成功后写穿 Redis AOF，应用重启先恢复快照；Redis 故障时保留进程内副本并记录 warn。
N 个前端客户端共享同一份缓存。

调度线程池固定 `spring.task.scheduling.pool.size=2`。实时行情与基金估值使用独立单飞保护；
基金估值每轮固定 25 秒预算，不续期，截止后保存基金和批量分页断点并在下轮继续。

### 外部调用预算与降级链

- 东方财富客户端共享 20 req/s 令牌桶；该值经本机短压测验证，线上异常时应根据指标下调。
- 手工 Feign client 统一 `connectTimeout=1s/readTimeout=3s`，`Retryer.NEVER_RETRY`。
- 东方财富共享限流器单次最多等待 1 秒；超时进入下一个数据源。
- 净值/字典：东方财富 -> 同花顺；盘中估值：同花顺分钟线 -> AKShare 参考的东方财富静态估值页批量源 -> ETF IOPV 与同花顺最近确认净值配对(仅交易型 ETF) -> 旧 fundgz 兼容回退；指数 K 线：中证指数公司 -> 腾讯 -> 同花顺 -> 东方财富。
- 深交所 `0.*` 指数不属于中证源覆盖范围，必须直接跳过中证源；不得先请求再靠解析异常降级。
- 同轮行情刷新按唯一 `benchmarkIndexCode` 拉取并复用指数 K 线；本地无缓存拉 400 根，已有缓存只拉最近 10 根并覆盖重叠日期。
- 东方财富与同花顺基金净值接口均只提供完整历史序列，源端无法按日期增量；数据库仍只写入本地最新日期之后的数据。
- `null`、空 Collection、空 `IndexKline.bars` 均记为 `empty` 并继续降级；`UnsupportedOperationException` 记为 `unsupported`。
- 同花顺净值需要单位/累计两次请求并按日期关联；字典使用 `fund.10jqka.com.cn/data/Net/info/...`；K 线使用 `d.10jqka.com.cn/v6/line/.../last.js`。
- 同花顺盘中估值使用 `gz-fund.10jqka.com.cn` 分钟线，取最后有效点相对基准净值计算涨跌幅。
- 本机 AKShare 1.18.12 的 `fund_value_estimation_em` 实际请求 `api.fund.eastmoney.com/FundGuZhi/GetFundGZList`，当前实测 `Data=null`；Java 兼容客户端改参考其基金估值页面入口 `fund.eastmoney.com/fundguzhi{page}.html`，按页解析 `data-gz`，并在进程内缓存批量结果 1 分钟。静态页仅覆盖其实际返回的基金类别；ETF `f441` IOPV 仅在与同花顺最近确认单位净值配对后进入交易型 ETF 估值分支，交易价格和历史净值不直接当作普通基金盘中涨跌幅。
- AKShare `stock_zh_index_daily_tx` 与 `stock_zh_a_hist_tx` 共用 `proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get`，参数为 `symbol,day,start,end,640,qfq`（股票接口按复权参数变化），响应变量为 `kline_dayqfq`；Java 映射 `1.*`/`0.*` 为 `sh`/`sz`，CSI `2.*` 直接跳过腾讯源。当前业务只消费指数 K 线，因此不额外接入股票历史、分笔和 A+H 交易接口，周/月在本地聚合。
- AKShare `fund_etf_spot_ths` 返回 ETF 最近已公布单位净值/日增长率，不是分钟估值；`fund_open_fund_daily_em`、`fund_etf_fund_daily_em` 是确认净值；`fund_etf_spot_em` 的 `f441` 是 ETF IOPV，当前由独立分支与同花顺最近确认净值配对计算估算涨跌；`fund_lof_spot_em`/新浪 ETF 接口是场内交易价，不能直接写入普通基金盘中估值缓存。
- AKShare 的新浪 ETF/LOF 行情和 `stock_zh_index_daily` 仍属于场内交易价或历史行情；新浪指数响应需要其 JS 解码，当前不进入普通基金估值链，也不作为腾讯源的替代实现。
- 外部请求必须在数据库事务外；只把最终增量落库放进短事务。

### 东方财富字段缩放契约

| 字段类型 | 字段 | 缩放 | 示例 |
|---------|------|------|------|
| 价格类 | f2, f4 | ÷100 | f2=404364 → 4043.64 |
| 百分比类 | f3 | ÷100 | f3=37 → 0.37% |
| 金额类 | f5, f6, f62, f66, f72, f78, f84 | 原值(元) | f6=1465563104853.7 |
| 家数类 | f104, f105, f106 | 非负整数原值 | f104=1542 表上涨 1542 只，f106 表平盘家数 |

**陷阱**:f2/f3/f4 必须在解析器里 ÷100 还原,f6/f62 等金额字段原值。
混用会导致指数点位差 100 倍或涨跌幅放大 100 倍。

### 市场宽度契约

- 固定汇总三个市场 secid:`1.000001`(沪市)、`0.399001`(深市)、`0.899050`(北交所)。
- `f104` 为上涨家数，`f105` 为下跌家数，`f106` 为平盘家数；三个固定市场分别求和后写入 `MarketBreadthSnapshot`。
- 同花顺 `https://q.10jqka.com.cn/api.php?t=indexflash` 的 `zdt_data.zd_time`、`ztzs`、`dtzs` 必须为等长非空数组；取末项的非负整数作为涨停、跌停家数。客户端每轮先访问主页建立内存 Cookie 会话，再请求统计接口；不得保存、配置或记录 Cookie 值。
- 这些字段表示当日有涨跌状态的沪深京股票，不等于全部上市 A 股总数，前端文案使用“大盘涨跌 / 沪深京股票”。
- 市场宽度与用户 `watchedIndices` 解耦。缓存刷新时将自选 secid 与固定三个 secid 去重合并，一次调用 `fetchIndexRealtimeRaw`，再分别投影到 `indexCache` 与 `breadthCache`。
- 任一固定市场缺失，任一 `f104/f105/f106` 缺失、非整数、负数，或同花顺主页、接口、解析失败时，不得发布部分市场合计，已有完整 `breadthCache` 保持不变。旧 Redis 快照缺 `flatCount` 时不恢复，直到取得上涨、平盘、下跌、涨停、跌停五项完整快照。
- 前端进度条左红表示上涨、右绿表示下跌，比例分母仅为 `risingCount + fallingCount`，平盘不参与。合计为 0 或接口数据为空时显示空轨道。

## Scenario: 工作台市场量价状态

### 1. Scope / Trigger

- 触发：工作台需要用上证指数涨跌幅和量比展示一个市场量价判断，同时保持外部请求次数不变。

### 2. Signatures

```text
东方财富批量字段: f3, f10, f12, f13, f124
MarketVolumePriceSnapshot(changePct, volumeRatio, quoteTime)
GET /api/market/volume-price
  -> { state, phase, changePct, volumeRatio, quoteTime }
```

### 3. Contracts

- 上证固定使用 `1.000001`；`f3 / 10000` 为小数涨跌幅，`f10 / 100` 为量比，`f124` 为 Unix 秒。
- `MarketRealtimeCache` 与指数、市场宽度共用同一次批量请求。解析成功才替换量价快照；失败保留旧值，不增加请求。
- Redis `Snapshot.marketVolumePrice` 可空；旧 JSON 缺字段时恢复为 `null`，其他行情字段照常恢复。
- 量比 `>=1.5` 为放量、`<=0.5` 为缩量、其余为平稳；涨跌幅正/负/零分别为上涨/下跌/平盘。
- 交易日交易时段及午休返回 `INTRADAY_ESTIMATE`，盘前、收盘及非交易日返回 `CLOSED`。仅交易时段检查两分钟时效。
- `state` 为 `HIGH_UP|LOW_UP|HIGH_DOWN|LOW_DOWN|NORMAL_UP|NORMAL_DOWN|FLAT|UNAVAILABLE`。前端只映射文案，不能重新推导状态。

### 4. Validation & Error Matrix

| 条件 | 结果 |
|------|------|
| `f3/f10/f124` 缺失、非数值，量比 `<=0` 或时间非法 | 不发布新快照，保留旧缓存 |
| 交易中快照超过两分钟 | `UNAVAILABLE`，数值置空，保留原始 `quoteTime` |
| 盘前快照不是上一交易日 | `UNAVAILABLE` |
| 交易中、午休或收盘快照不是当日 | `UNAVAILABLE` |
| 非交易日快照不是最近交易日 | `UNAVAILABLE` |
| 前端状态未知或数值为空 | 显示“量能观察中”，不生成方向性提醒 |

### 5. Good / Base / Bad Cases

- Good：当日 `changePct=0.0037`、`volumeRatio=1.68` 返回 `HIGH_UP` 和完整行情时间。
- Base：`0.5 < volumeRatio < 1.5` 按价格方向返回 `NORMAL_UP` 或 `NORMAL_DOWN`；涨跌幅为零返回 `FLAT`。
- Bad：第二次响应缺 `f10` 时缓存仍保留旧快照，但查询层不得把过期或错日快照冒充当前结论。

### 6. Tests Required

- 解析器：断言 `f3/f10/f124` 缩放与缺失、非法字段降级。
- 缓存与 Redis：断言成功往返、旧 JSON 兼容和第二次缺 `f10` 保留旧快照。
- 查询：断言八种状态、`0.5/1.5` 边界、午休/盘前/收盘/非交易日和两分钟过期。
- Web 与前端：断言字段映射、不可用降级、工作台挂载和 `aria-live="polite"`。

### 7. Wrong vs Correct

```java
// Wrong: 失败时用服务器时间和零量比伪造当前行情
snapshot = new MarketVolumePriceSnapshot(changePct, BigDecimal.ZERO, clock.instant());

// Correct: 只发布数据源完整快照；查询层独立校验日期和时效
if (parsed != null) marketVolumePriceCache = parsed;
```

## Scenario: 工作台核心行情时效与完整宽度

### 1. Scope / Trigger

- 触发：工作台展示市场状态、最后成功快照时间和五项市场宽度，需要前后端与 Redis 共用同一时效契约。

### 2. Signatures

```text
GET /api/market/status → { marketState, updatedAt }
GET /api/market/breadth → { risingCount, fallingCount, flatCount, limitUpCount, limitDownCount }
```

```java
public Instant getMarketUpdatedAt();
```

### 3. Contracts

- `MarketRealtimeCache` 分别保存 `indicesUpdatedAt`、`breadthUpdatedAt`、`sectorsUpdatedAt`，仅在对应数据族成功替换时更新。
- Redis `Snapshot` 持久化三个时间；旧 JSON 缺字段时保持 `null`。
- 三个时间都存在时，`updatedAt` 取其最旧值；否则返回 `null`。
- 市场状态使用现有交易日历与 `Asia/Shanghai`：`PRE_OPEN`、`TRADING`、`LUNCH_BREAK`、`CLOSED`、`NON_TRADING_DAY`。

### 4. Validation & Error Matrix

| 条件 | 结果 |
|------|------|
| 固定市场缺失或 `f104/f105/f106` 任一无效 | 保留旧完整宽度与旧时间 |
| 同花顺涨跌停失败 | 保留旧完整宽度与旧时间 |
| 指数、宽度、行业任一时间为 `null` | `updatedAt=null` |
| 非交易日 | `marketState=NON_TRADING_DAY` |

### 5. Good / Base / Bad Cases

- Good：三类数据都成功刷新，返回三个时间中最旧值和完整五项宽度。
- Base：旧 Redis JSON 缺时间或 `flatCount`，可反序列化，但返回空时效/宽度直到下次成功刷新。
- Bad：外部响应为空或部分市场缺字段，不得推进对应时间或发布部分快照。

### 6. Tests Required

- 解析器：`f106` 完整求和，缺失/负数/非整数返回空宽度。
- 缓存：失败刷新不改旧快照和时间，三类成功时间取最小值。
- Redis：新字段往返一致，旧 JSON 缺字段为 `null`。
- 状态：覆盖 09:30、11:30、13:00、15:00 边界与非交易日。

### 7. Wrong vs Correct

```java
// Wrong: 读取接口或部分刷新成功时伪造整体时效
updatedAt = clock.instant();

// Correct: 只在三类成功时间齐全时返回最旧值
if (indicesUpdatedAt == null || breadthUpdatedAt == null || sectorsUpdatedAt == null) return null;
return Stream.of(indicesUpdatedAt, breadthUpdatedAt, sectorsUpdatedAt)
        .min(Instant::compareTo).orElse(null);
```

### 交易时段判断契约

- 时区:`Asia/Shanghai`。
- A 股完整行情时段:9:30-11:30(上午)、13:00-15:00(下午)。
- 交易日查询参数必须使用 `ChinaTradingDate.toUtcDate(clock.instant())`，即北京时间自然日对应的 UTC 00:00 标签
- 非交易日:`trading_calendar` 表无记录或 `is_trading_day=false` 时不刷新 A 股完整行情。
- 基金估值窗口仅为已确认的 A 股交易日 09:30-11:30、13:00-15:00；晚间、跨夜和非交易日不刷新基金估值。

### 基金估值范围契约

- `refreshFundEstimates()` 遍历平台当前跟踪产品并按产品去重；`investmentTarget=QDII` 不调用任何盘中估值源，已有估值和分时缓存清除并置 `UNAVAILABLE`。
- 东方财富静态估值页与 ETF IOPV 仍按页获取；静态页只保留本轮平台基金代码，命中当前基金或收齐目标代码后立即保存下一页并返回。每页前检查本轮截止时间，未完成批次在 1 分钟内从下一页续刷，过期后从第一页重建；缓存有效期从最后响应完成时开始计算。
- 非 QDII 的 `HOLDING` 与 `PENDING_HOLDING` 都必须进入估值缓存；观察池基金也展示盘中三态涨跌。
- 单只基金结果必须区分 `AVAILABLE/UNAVAILABLE/STALE/TIMEOUT/PARSE_ERROR`，不能把空响应和超时压成同一布尔值。
- 只接受 `estimateTime` 属于北京时间当天的快照；旧日期为 `STALE`，空响应/产品不提供为 `UNAVAILABLE`，时间格式损坏为 `PARSE_ERROR`。
- 货币基金和 REIT 本期不进入普通估值/净值源，状态为 `UNAVAILABLE`，前端显示中性“暂无估值”。
- 后续本次成功拉到当天估值时覆盖缓存并置为 `AVAILABLE`。
- 单只基金估值发生 `TIMEOUT` 或 `PARSE_ERROR` 后，5 分钟内不重复请求两条外部源，保留失败状态；冷却到期后重试，任一源成功即清除冷却并恢复 `AVAILABLE`。同花顺首选源单独失败时也冷却 5 分钟，其间直接尝试东方财富后备源，避免后备成功时仍反复记录同花顺失败。
- `STALE/NOT_ATTEMPTED` 表示该基金北京时间当日估值阶段尚未开始：今日涨跌为 0，持仓市值与总盈亏使用最近一期已确认单位净值。
- 当日估值出现后，空响应、超时或解析错误仍返回未知，不得用最近确认净值冒充交易中的当前值。
- 两个 `ApplicationReadyEvent` 监听器都必须标记 `@Async`；指数/板块/资金与基金估值的外部 I/O 均不得占用 readiness 事件线程。
- 实时行情监听器调用 `refreshRealtimeWithoutEstimates()`；独立监听器调用基金估值预热，保证盘后重启也能重新取得当日最后估值。
- 普通基金今日净值未落库时禁止用 T-1 对 T-2 冒充今日涨跌。
- QDII 收益确认日按最新净值 `firstSeenAt` 的北京时间自然日判定，不按可能滞后的 `navDate` 判定。仅确认日使用按 `navDate DESC` 取得的最新两期净值计算今日收益；次日起今日涨跌/盈亏固定为 0，最近确认净值仍用于市值和总盈亏，且不混用盘中估值。
- 收益测试不能只手工构造 `InvestmentTarget.QDII`；必须另有创建链路测试证明真实基金会持久化该分类。

### 14:50 串行契约

- 14:30/14:40 仅执行 `fetchBatch(0/1)`。
- 14:50 的唯一调度入口先执行 `fetchBatch(2)`，返回后再调用 `SignalGenerationJob.generateDaily()`。
- `SignalGenerationJob` 不得再声明独立的同秒 `@Scheduled`，手动行情刷新和手动信号生成入口仍独立可用。

---

## Validation & Error Matrix

| 条件 | 行为 |
|------|------|
| 指数/市场宽度/板块/资金接口超时或失败 | 保留对应旧缓存 + 记 warn(不抛异常) |
| 单只基金估值空响应/产品不支持 | 删除旧估值，`estimateStatus=UNAVAILABLE`，不标记失败 |
| 单只基金 `estimateTime` 非北京时间当天 | 删除旧估值，`estimateStatus=STALE`，不标记失败 |
| 单只基金连接/读取超时 | 删除旧估值，`estimateStatus=TIMEOUT`，`estimateFetchFailed=true` |
| 单只基金响应结构损坏 | 删除旧估值，`estimateStatus=PARSE_ERROR`，`estimateFetchFailed=true` |
| 后续重新拉到当天有效估值 | 覆盖缓存，`estimateStatus=AVAILABLE`，清除失败状态 |
| 指数/市场宽度/板块/资金缓存为空(首次启动/全失败) | 返回空列表/null,前端显示「暂无数据」 |
| 基金估值尚未完成首次尝试 | 视为估值阶段尚未开始，今日涨跌为 0，当前市值使用最近确认净值 |
| 用户未配置 watchedIndices | 返默认列表(上证+沪深300+创业板),不抛错 |
| 上涨、下跌、涨停、跌停四项完整 | 汇总 `f104/f105` 与同花顺分钟数组末项并更新 `breadthCache` |
| 任一市场、家数字段、同花顺主页或接口缺失/失败 | 保留旧完整 `breadthCache`;首次无缓存时接口 data=null |
| 今日净值未落库且有估值缓存 | 返回当日有效估值并标记 `isEstimated=true` |
| 今日净值未落库且状态为 `STALE/NOT_ATTEMPTED` | 今日涨跌为 0，当前市值/总盈亏使用最近确认净值，不计算昨日涨跌 |
| 今日净值未落库且状态为 `UNAVAILABLE` | 今日涨跌/盈亏返回未知；持仓市值/总盈亏使用最近确认净值 |
| 今日净值未落库且最近一次估值失败 | `FundView.estimateFetchFailed=true`;持仓市值/总盈亏使用最近确认净值 |
| 今日净值已落库但估值曾失败 | 使用实际净值,`estimateFetchFailed=false` |
| 任一持仓今日盈亏未知 | `dailyPnlTotal` 汇总其余可用持仓，`dailyCoveredFundCount` 标明覆盖数；无任何覆盖时返回 null |
| 持仓基金存在估值失败 | `PortfolioSummaryView.estimateFetchFailedCount` 返回失败持仓数,前端明确显示失败而非普通 `-` |
| 观察池基金 | 与持仓基金一样进入估值缓存 |
| 第三批行情异常抛出 | 本次不继续生成信号 |
| 应用启动 | 后台异步预热指数/板块/资金和基金估值；外部接口延迟不阻塞健康检查 |
| 晚间净值远端日期晚于本地最新日期 | 不要求等于今天，在短事务内增量落库 |
| FOF/QDII 新净值仍滞后今天 | 按真实 navDate 落库，不受 fundgz 状态阻断 |
| QDII 最新净值 `firstSeenAt` 为北京时间今天且已有两期净值 | 按 `navDate DESC` 的最新两期净值计算收益，`isEstimated=false`，展示最新 `valuationDate` |
| QDII 最新净值 `firstSeenAt` 早于北京时间今天 | 今日涨跌/盈亏为 0；最近确认净值继续计算市值/总盈亏，不读取盘中估值 |
| 存量基金名称含 QDII 且 `investment_target IS NULL` | V26 回填为 `QDII` |
| 存量基金已有非空 `investment_target` | V26 保持原值，不覆盖人工或既有分类 |

---

## Good/Base/Bad Cases

- **Good**:交易时段,前端 5s 轮询指数,后端 30s 刷新缓存,用户看到近实时行情
- **Good**:一次指数批量请求同时包含自选指数与沪深京固定市场,两个缓存独立投影
- **Base**:市场宽度首次预热失败,组合收益仍正常展示,进度条为空轨道
- **Good**:15:20 盘后发布重启,异步预热 fundgz 后全仓收益继续显示今日估值
- **Good**:QDII 不调用盘中估值源，旧估值缓存清除为 `UNAVAILABLE`，收益继续按确认净值发现日结算
- **Good**:QDII 的 7 月 17 日净值在 7 月 20 日首次发现，7 月 20 日按该净值与上一期计算收益并显示真实净值日；同日发现多条时取 `navDate` 最大者
- **Good**:到 7 月 21 日没有新发现净值时，QDII 今日涨跌/盈亏为 0，市值和总盈亏仍按 7 月 17 日最新确认净值计算
- **Good**:通过基金搜索创建名称含 QDII 的基金，`investmentTarget` 自动保存为 `QDII`
- **Good**:东方财富启动预热超时,应用 readiness 仍可及时完成,缓存等待后台任务或下次定时刷新
- **Good**:某基金本轮超时后旧估值立即消失,总览显示「估值拉取失败」;下一轮成功后自动恢复
- **Good**:货币基金/REIT 不调用普通估值源，页面中性显示“暂无估值”
- **Good**:东方财富净值空结果后，同花顺单位/累计净值按日期关联成功返回
- **Good**:14:50 第三批快照完成后才读取快照生成信号
- **Base**:估值接口暂时失败且缓存为空,今日涨跌显示未知而不是昨日值
- **Bad**:fundgz 返回昨日 `gztime`,仍继续作为今日估值使用
- **Bad**:数据源返回空集合时直接结束降级链，导致真实备用源永远不执行
- **Bad**:用 fundgz.jzrq 必须等于今天作为净值入库门卫，导致 QDII/FOF 漏更新
- **Bad**:估值已进入当日阶段后发生空响应/失败,收益服务仍用上一期已公布净值冒充当前持仓市值/总盈亏
- **Bad**:普通基金今日净值未落库时用最近两期落库净值计算,把昨日收益标成今日收益
- **Bad**:QDII 只判断已有两期净值而忽略最新净值 `firstSeenAt`，导致次日重复结算同一段收益
- **Bad**:观察列表把独立估值接口结果覆盖到已由后端选定的 QDII 确认收益
- **Bad**:测试手工设置 QDII 枚举但真实创建链路从不写该字段，导致测试通过而生产分支永远不命中
- **Bad**:实时任务用上海午夜 Instant 查询 UTC DATE 行,导致交易日永远错位 8 小时
- **Bad**:行情抓取和信号生成使用两个同秒 cron,信号可能先读到缺失快照
- **Bad**:从用户自选指数的 `f104/f105` 相加市场宽度,会因沪深300等成分范围重叠而重复计数

---

## Tests Required

- `EastmoneyJsParserRealtimeTest`:实时行情解析测试覆盖正常响应、空响应、字段缺失
  - 断言点:f2÷100 还原、f3÷100 还原、f6 原值、f62 缺失为 null；北向资金解析仅作为遗留兼容回归
- `EastmoneyJsParserRealtimeTest`:市场宽度断言三个固定市场完整时正确求和；缺市场、缺 `f104/f105` 时返回 null。
- 缓存层降级测试:指数/市场宽度等仍验证旧缓存保留；基金估值必须单独验证成功后空响应、异常、旧日期都会删除旧值。
- `MarketRealtimeRefreshJobTest`:固定 Clock,断言北京时间自然日映射到 UTC 00:00 日历标签；仅 A 股交易时段刷新基金估值，晚间和跨夜不调用刷新命令。
- `MarketRealtimeCacheTest`:断言持仓与观察池普通基金都调用 `fetchEstimateResult`；QDII、货币基金和 REIT 不调用并置 `UNAVAILABLE`，QDII 已有缓存必须清除。
- `MarketRealtimeCacheTest`:固定 `Clock`,断言超时/解析错误为失败，空响应/旧日期为中性状态，后续成功恢复 `AVAILABLE`。
- `MarketRealtimeCacheTest`:断言两个启动事件都带 `@Async`，实时行情事件不查询基金列表，基金估值事件填充估值缓存。
- `ThsJsParserTest` / `ThsMarketDataSourceIntegrationTest`:断言净值双请求日期关联、字典 JSONP、K 线 callback、指数代码映射。
- `MarketDataSourceChainTest`:断言空 Collection/空 K 线继续降级，全失败抛 `MARKET_DATA_ALL_SOURCES_FAILED`。
- `ExternalClientConfigTest`:断言 connect=1s/read=3s；`RateLimiterTest` 断言最大等待预算。
- `DailyNavConfirmServiceTest`:断言不依赖 fundgz，FOF/QDII 滞后日期仍可增量入库，次日上午可按指定交易日跨夜补拉。
- `MarketRealtimeCacheTest`:断言一次指数请求同时包含自选与固定市场；残缺响应不覆盖旧 `breadthCache`。
- `EastmoneyJsParserRealtimeTest` / `RealtimeMarketOverviewQueryHandlerTest`:断言量比缩放、四种主要组合、平稳/平盘、时段/交易日和两分钟过期边界。
- `MarketRealtimeCacheTest`:断言第二次批量响应缺少 `f10` 时保留旧量价快照。
- `DailyChangeResolverTest`:断言 STALE/NOT_ATTEMPTED 返回 0，AVAILABLE 使用估值，UNAVAILABLE/TIMEOUT/PARSE_ERROR 返回未知，当日净值始终优先。
- `FundPnlServiceTest`:断言估值阶段开始前使用最近确认单位净值；估值失败时当前持仓市值/总盈亏未知且组合失败数正确；当日净值已入库时忽略估值失败状态。
- `FundPnlServiceDateTest`:固定 `Clock`，断言 QDII 最新净值 `firstSeenAt` 的北京时间当天使用按 `navDate DESC` 取得的最新两期净值；次日今日涨跌/盈亏为 0，但市值/总盈亏仍使用最新确认净值；单基金与批量结果一致。
- `querySafety.test.js`:断言观察列表合并独立估值接口时保留 QDII 的确认收益、`isEstimated=false` 和 `valuationDate`。
- `FundServiceTest`:通过真实创建入口断言名称含 QDII 时 `FundView.investmentTarget=QDII`；CI 在 PostgreSQL 上执行 V26 与 Hibernate validate。
- `MarketDataFetchJobTest`:用 `InOrder` 断言 `fetchBatch(2)` 完成后才生成信号。

---

## Wrong vs Correct

### Wrong:前端直接轮询东方财富

```javascript
// 错误:N 个前端 × 5s 轮询 × 直接调东方财富 = 请求量随用户数失控
useQuery({
    queryFn: () => fetch('https://push2.eastmoney.com/...'),
    refetchInterval: 5_000,
});
```

### Correct:前端轮询后端缓存,后端定时刷新

```javascript
// 正确:前端读后端内存副本,后端 30s 刷一次东方财富并写穿 Redis
useQuery({
    queryFn: () => get('/api/market/indices/realtime'),
    refetchInterval: 5_000,
});
```

后端 `MarketRealtimeCache` 用内存副本 + Redis AOF + `@Scheduled` 30s 刷新,
前端 N 客户端共享同一份缓存,东方财富侧请求量恒定(与客户端数无关),应用发布重启后从 Redis 恢复。

### Wrong:盘后重启后等待下一交易时段

```java
@Async
@EventListener(ApplicationReadyEvent.class)
public void onApplicationReady() {
    refreshRealtimeWithoutEstimates();
}

return dailyChangePct(latestNav, previousNav); // T-1 vs T-2 是昨日涨跌
```

### Correct:启动完成后异步预热,按状态区分估值前与失败

```java
@Async
@EventListener(ApplicationReadyEvent.class)
public void warmFundEstimatesAfterReady() {
    refreshFundEstimates();
}

// STALE/NOT_ATTEMPTED:今日涨跌 0,市值使用最近确认净值。
// UNAVAILABLE/TIMEOUT/PARSE_ERROR:返回未知,不冒充交易中的当前值。
```

### Wrong:QDII 只要有两期净值就重复展示收益

```java
boolean confirmedNavSelected = qdii && latestTwo.size() >= 2;
```

### Correct:QDII 只在最新净值的发现日结算一次

```java
boolean confirmedNavSelected = qdii
        && latestTwo.size() >= 2
        && latestTwo.get(0).getFirstSeenAt() != null
        && ChinaTradingDate.toUtcDate(latestTwo.get(0).getFirstSeenAt())
                .equals(ChinaTradingDate.toUtcDate(clock.instant()));
```

### Wrong:只在收益测试里手工设置 QDII

```java
fund.setInvestmentTarget(InvestmentTarget.QDII); // 真实创建链路没有写入，生产仍为空
```

### Correct:创建时持久化并迁移存量空分类

```java
fund.setInvestmentTarget(inferInvestmentTarget(request.fundName()));
```

```sql
UPDATE fund SET investment_target = 'QDII'
WHERE investment_target IS NULL AND fund_name ILIKE '%QDII%';
```

### Wrong:基金估值失败沿用通用旧缓存降级

```java
fundEstimateService.fetchEstimate(code)
        .ifPresent(snapshot -> estimateCache.put(code, snapshot));
// empty/异常时旧 snapshot 仍留在 map,下一轮会继续冒充今日估值。
```

### Correct:基金估值按本轮结果替换并校验自然日

```java
FundEstimateResult result = fundEstimateService.fetchEstimateResult(code);
EstimateStatus status = classifyFreshness(result);
if (status == EstimateStatus.AVAILABLE) {
    estimateCache.put(code, result.snapshot());
}
estimateStatuses.put(code, status);
```

### Wrong:按自选指数汇总市场宽度

```java
// 错误:自选可能同时含沪深300、上证50、创业板指,成分范围重叠且会随用户配置变化。
for (IndexRealtimeSnapshot index : indexCache) {
    rising += index.risingCount();
}
```

### Correct:固定市场口径并复用一次请求

```java
Set<String> requested = new LinkedHashSet<>(watchedSecids);
requested.addAll(MARKET_BREADTH_SECIDS);
String raw = push2Client.fetchIndexRealtimeRaw(String.join(",", requested));

indexCache = projectWatchedIndices(raw, watchedSecids);
MarketBreadthSnapshot breadth = EastmoneyJsParser.parseMarketBreadth(raw, MARKET_BREADTH_SECIDS);
if (breadth != null) {
    breadthCache = breadth;
}
```

---

## Design Decision: 当前工作台使用行业主力资金

**Context**:设计阶段设想展示「北向 + 主力 + 超大单 + 大单 + 中单 + 小单」六项全市场汇总。

**验证发现**:东方财富全市场资金汇总接口(`fflow/daykline/get`)返回 `rc:102 data:null`,
结构不稳定。板块级资金(`clist` 的 f62 主力净流入)可靠。

**Decision**:当前工作台以板块级主力资金为主合同，随 `SectorSnapshot.mainforceNet` 返回并在板块组件展示。
北向资金 `MoneyFlowSnapshot` 与既有接口保留为遗留兼容能力，但不作为当前页面验收项。
全市场主力/超大单等汇总留 follow-up,待找到稳定接口再做。

**Extensibility**:`MoneyFlowSnapshot` 是 record,未来加字段只需扩 record + 解析器,
不影响现有 API 契约(新字段对旧前端透明)。

---

## Scenario: 基金详情当日分时

### 1. Scope / Trigger

- 基金详情需要当日分钟曲线，不能由浏览器直连外部行情源或用单点估值拼接。

### 2. Signatures

```text
GET /api/funds/{fundId}/intraday -> FundIntradayView | null
GET /api/portfolio-funds/{portfolioFundId}/intraday -> FundIntradayView | null
MarketRealtimeCache.getIntraday(Long fundId) -> FundIntradayChart | null
```

`FundIntradayView` 返回 `estimateDate`、`baseNav`、按 `HH:mm` 升序的
`points[{time, nav}]` 和数据源声明的 `tradingSessions[{start, end}]`。

### 3. Contracts

- 同花顺分钟估值响应一次解析出估值快照与分钟点；东方财富 fundgz 仅作为原有单点估值后备。
- `tradingSessions` 透传同花顺的 `HH:mm` 交易段；前端按每段的起止分钟展开时间轴，午休不生成槽位，不得硬编码单一市场时段。
- 已到达分钟只使用真实净值；尚未到达的槽位只保留 `timestamp`，不得写入 `close`、`value` 或估算价格。
- klinecharts v9 默认 `barSpace=8`，移动端会只显示末尾约 25 个分钟槽；有交易段时必须清除右侧偏移，并按主绘图区宽度减右轴预留宽度后除以槽位数动态计算 `barSpace`，最小值为 1。绘图区至少容纳槽位数加右轴宽度，移动端才可滚动到完整交易段，桌面端也不会把曲线挤在左侧。
- 仅当估值状态为 `AVAILABLE` 且分钟点不少于两个时，写入内存副本和 Redis `Snapshot.intradayCharts`。
- 用户请求只读缓存；前端交易时段每 30 秒轮询后端，不得请求同花顺。

### 4. Validation & Error Matrix

| 条件 | 行为 |
| --- | --- |
| 同花顺当日有效点 >= 2 | 缓存并返回分时图 |
| 同花顺有效点 < 2 | 保留单点估值语义，分时图为空 |
| 同花顺失败、过期或解析失败 | 清除旧分时缓存，分时图为空 |
| 东方财富后备成功 | 保留既有估值，分时图为空 |
| `tradingSessions` 为 `0930-1130,1300-1500` | 时间轴覆盖 09:30 到 15:00，午休区间无槽位 |
| 当前时间早于某分钟槽 | 保留时间刻度但价格为空，不绘制未来曲线 |

### 5. Good/Base/Bad Cases

- **Good**:同花顺 243 个北京时间当日点进入缓存，详情页展示“今日分时”。
- **Good**:盘中仅有 09:30 到当前点时，时间轴仍延伸到 15:00，未来区域无曲线。
- **Base**:盘前只返回一个点，今日涨跌可用但分时页显示空态。
- **Bad**:前端只提交完整分钟数组但沿用 klinecharts 默认 `barSpace`，移动端仍从收盘前十几分钟开始显示。
- **Bad**:有交易段时无条件固定 `barSpace=1`，移动端虽然能容纳全部槽位，桌面端却把整天曲线压缩在左侧。

### 6. Tests Required

- `FundEstimateServiceTest` 断言同花顺结果携带完整分钟点且不调用东方财富。
- `MarketRealtimeCacheTest` 断言两点曲线可读、无曲线结果清除旧缓存。
- `ThsJsParserTest`、Redis 网关和 HTTP View 测试断言交易段从 `0930-1130,1300-1500` 透传，旧 Redis 缺字段仍可读。
- 前端组件测试断言午休不生成槽位、未来槽只有时间戳、百分比末点按 `baseNav` 计算，并在不同绘图区宽度和 `resize` 后断言动态 `barSpace`。
- 前端 Tab 测试断言默认分时并可切换 K 线 / 走势图。

### 7. Wrong vs Correct

```java
// Wrong: 在 Controller 中触发外部请求，且把后备单点伪造成曲线。
return fundEstimateService.fetchEstimate(code);
```

```java
// Correct: 只暴露后台刷新时写入的同花顺分钟线缓存。
return FundIntradayView.from(marketRealtimeCache.getIntraday(fundId));
```

```javascript
// Correct: 未来分钟保留轴位置，但不伪造价格。
return {timestamp};
```

---

## Scenario: 中证指数 K 线非法 OHLC

### 1. Scope / Trigger

- 触发：中证指数接口返回的历史 K 线可能带有零价格占位行，但成交量仍有值；这些行进入 `index_kline` 会把前端价格轴拉到 0。
- 适用：所有通过 `CsindexJsParser.parseIndexKline(String rawJson, String indexCode)` 进入行情降级链的中证指数日 K。

### 2. Signatures

```java
CsindexJsParser.parseIndexKline(String rawJson, String indexCode) -> IndexKline
IndexKline.Bar(Instant date, BigDecimal open, BigDecimal close,
               BigDecimal high, BigDecimal low, long volume)
```

### 3. Contracts

- `open`、`high`、`low`、`close` 必须是 JSON 数值且严格大于 0。
- 缺失、`null`、非数值或非正 OHLC 的行不得构造 `IndexKline.Bar`。
- 混合响应只过滤坏行，保留有效行及既有成交量单位换算；不因成交量为 0 单独丢弃价格有效的行。
- 过滤后无有效行时抛 `IllegalStateException`，由 `MarketDataSourceChain` 继续尝试后备数据源；禁止返回零值 K 线。
- 落库和前端不重复实现这条数据源校验；`index_kline` 只接收解析后的有效柱线。

### 4. Validation & Error Matrix

| 条件 | 行为 |
| --- | --- |
| 四个 OHLC 均为正数 | 构造并返回该 bar |
| 任一 OHLC 缺失、非数值或小于等于 0 | 跳过该行 |
| 响应中有合法行和非法行 | 返回合法行，保留合法行成交量 |
| 所有行被过滤 | 抛 `IllegalStateException`，触发数据源降级 |

### 5. Good/Base/Bad Cases

- **Good**：一行 `open/high/low=0` 与一行合法 OHLC 混合时，只落合法行，价格轴不包含 0。
- **Base**：合法 OHLC 且成交量为 0 时仍保留该 bar，避免把成交量语义误当价格有效性。
- **Bad**：直接调用 `row.path("open").decimalValue()` 并 upsert；缺失/零价格会作为有效 K 线污染缓存。

### 6. Tests Required

- `CsindexJsParserTest` 断言混合响应过滤零 OHLC 行、保留日期和成交量换算。
- `CsindexJsParserTest` 断言全部非法行抛 `IllegalStateException` 并携带指数代码。
- `MarketDataSourceChainTest` 保持断言解析失败继续降级且全失败抛 `MARKET_DATA_ALL_SOURCES_FAILED`。
- 发布后对受影响 `index_kline` 做只读非正 OHLC 统计和页面 K 线/成交量验证；生产写库前必须完成备份。

### 7. Wrong vs Correct

```java
// Wrong: 缺失字段或零值也会生成可落库的 Bar。
new IndexKline.Bar(date, row.path("open").decimalValue(), close, high, low, volume);
```

```java
// Correct: 在外部数据解析边界过滤非法价格；全坏响应交给降级链。
if (!openNode.isNumber() || open.signum() <= 0) continue;
```
