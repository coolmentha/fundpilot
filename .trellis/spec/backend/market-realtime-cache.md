# Realtime Market Cache Layer

> 行情工作台实时数据缓存层契约。本任务(task 07-04-market-dashboard-pivot)引入,
> 解决「前端 5-10s 高频轮询 vs 东方财富 2 req/s 限流」矛盾。

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
GET /api/funds/{fundId}/kline?period=daily|weekly|monthly → KlineView
```

### 缓存服务(`MarketRealtimeCache`)

```java
public List<IndexRealtimeSnapshot> getIndices();        // 读 volatile 字段
public MarketBreadthSnapshot getBreadth();              // 沪深京股票涨跌家数
public List<SectorSnapshot> getSectors();
public MoneyFlowSnapshot getMoneyFlow();
public Map<String, FundEstimateSnapshot> getEstimates(List<String> codes); // 只读缓存,不拉外部接口
public void refreshAll();                               // 全量刷新(含估值)
public void refreshRealtimeWithoutEstimates();          // 仅刷新指数/市场宽度/板块/资金
@Async public void onApplicationReady();                // 启动完成后后台预热实时行情
@Async public void warmFundEstimatesAfterReady();       // 启动完成后后台预热基金估值
```

### 定时任务(`MarketRealtimeRefreshJob`)

```java
@Scheduled(cron = "*/30 * 9-14 * * MON-FRI", zone = "Asia/Shanghai")
public void refreshRealtime();  // 内部 isTradingHours() 二次过滤
```

---

## Contracts

### 数据刷新频率契约

| 数据类型 | 后端刷新 | 前端轮询 | 理由 |
|---------|---------|---------|------|
| 指数实时 | 30s | 5s | 单请求批量,快 |
| 板块涨跌 | 30s | 30s | 单请求 |
| 行业主力资金 | 30s | 30s | 随板块快照批量返回 |
| 基金估值 | **30s** | 10s | 盘中实时性优先,后台逐只刷新;失败立即失效该基金旧估值 |

**关键不变量**:前端轮询频率 > 后端刷新频率。前端读内存零外部请求,
N 个前端客户端共享同一份缓存。

### 东方财富字段缩放契约

| 字段类型 | 字段 | 缩放 | 示例 |
|---------|------|------|------|
| 价格类 | f2, f4 | ÷100 | f2=404364 → 4043.64 |
| 百分比类 | f3 | ÷100 | f3=37 → 0.37% |
| 金额类 | f5, f6, f62, f66, f72, f78, f84 | 原值(元) | f6=1465563104853.7 |
| 家数类 | f104, f105 | 非负整数原值 | f104=1542 表上涨 1542 只 |

**陷阱**:f2/f3/f4 必须在解析器里 ÷100 还原,f6/f62 等金额字段原值。
混用会导致指数点位差 100 倍或涨跌幅放大 100 倍。

### 市场宽度契约

- 固定汇总三个市场 secid:`1.000001`(沪市)、`0.399001`(深市)、`0.899050`(北交所)。
- `f104` 为上涨家数，`f105` 为下跌家数；三者分别求和后写入 `MarketBreadthSnapshot`。
- 这些字段表示当日有涨跌状态的沪深京股票，不等于全部上市 A 股总数，前端文案使用“大盘涨跌 / 沪深京股票”。
- 市场宽度与用户 `watchedIndices` 解耦。缓存刷新时将自选 secid 与固定三个 secid 去重合并，一次调用 `fetchIndexRealtimeRaw`，再分别投影到 `indexCache` 与 `breadthCache`。
- 任一固定市场缺失，或任一 `f104/f105` 缺失、非整数、负数时，解析结果为 null；不得发布部分市场合计，已有 `breadthCache` 保持不变。
- 前端进度条左红表示上涨、右绿表示下跌，比例分母仅为 `risingCount + fallingCount`，平盘不参与。合计为 0 或接口数据为空时显示空轨道。

### 交易时段判断契约

- 时区:`Asia/Shanghai`
- 时段:9:30-11:30(上午)、13:00-15:00(下午)
- 交易日查询参数必须使用 `ChinaTradingDate.toUtcDate(clock.instant())`，即北京时间自然日对应的 UTC 00:00 标签
- 非交易日:`trading_calendar` 表无记录或 `is_trading_day=false` 时不刷新
- 非交易时段:Job 的 cron 放宽到 9-14 点,靠 `isTradingHours()` 精细过滤

### 基金估值范围契约

- `refreshFundEstimates()` 遍历 `FundRepository.findAll()`，覆盖全部未软删基金。
- `HOLDING` 与 `PENDING_HOLDING` 都必须进入估值缓存；观察池基金也展示盘中三态涨跌。
- 单只基金拉取失败不能中断其他基金，但必须删除该基金旧估值并标记失败。
- 只接受 `estimateTime` 属于北京时间当天的快照；旧日期、空时间或无法解析的时间都按失败处理。
- 后续本次成功拉到当天估值时覆盖缓存并清除失败状态。
- 两个 `ApplicationReadyEvent` 监听器都必须标记 `@Async`；指数/板块/资金与基金估值的外部 I/O 均不得占用 readiness 事件线程。
- 实时行情监听器调用 `refreshRealtimeWithoutEstimates()`；独立监听器调用基金估值预热，保证盘后重启也能重新取得当日最后估值。
- 今日净值未落库且估值缓存缺失时，今日涨跌返回未知；禁止用 T-1 对 T-2 冒充今日值。

### 14:50 串行契约

- 14:30/14:40 仅执行 `fetchBatch(0/1)`。
- 14:50 的唯一调度入口先执行 `fetchBatch(2)`，返回后再调用 `SignalGenerationJob.generateDaily()`。
- `SignalGenerationJob` 不得再声明独立的同秒 `@Scheduled`，手动行情刷新和手动信号生成入口仍独立可用。

---

## Validation & Error Matrix

| 条件 | 行为 |
|------|------|
| 指数/市场宽度/板块/资金接口超时或失败 | 保留对应旧缓存 + 记 warn(不抛异常) |
| 单只基金估值拉取异常/空响应/解析为空 | 删除该基金旧估值,标记 `estimateFetchFailed`,不影响其他基金 |
| 单只基金 `estimateTime` 非北京时间当天 | 删除该基金旧估值,标记 `estimateFetchFailed` |
| 失败后重新拉到当天有效估值 | 覆盖估值缓存并清除 `estimateFetchFailed` |
| 指数/市场宽度/板块/资金缓存为空(首次启动/全失败) | 返回空列表/null,前端显示「暂无数据」 |
| 基金估值尚未完成首次尝试 | 今日估值未知,不提前声称拉取失败 |
| 用户未配置 watchedIndices | 返默认列表(上证+沪深300+创业板),不抛错 |
| 三个市场宽度字段完整 | 汇总 `f104/f105` 并更新 `breadthCache` |
| 任一市场或家数字段缺失 | 保留旧 `breadthCache`;首次无缓存时接口 data=null |
| 今日净值未落库且有估值缓存 | 返回当日 fundgz 估值并标记 `isEstimated=true` |
| 今日净值未落库且估值缓存为空 | 今日涨跌/盈亏返回未知，不回退昨日涨跌 |
| 今日净值未落库且最近一次估值失败 | `FundView.estimateFetchFailed=true`;当前持仓市值/总盈亏也返回未知 |
| 今日净值已落库但估值曾失败 | 使用实际净值,`estimateFetchFailed=false` |
| 任一持仓今日盈亏未知 | 全仓 `dailyPnlTotal` 返回 null，不展示部分合计；非估值失败原因可显示 `-` |
| 持仓基金存在估值失败 | `PortfolioSummaryView.estimateFetchFailedCount` 返回失败持仓数,前端明确显示失败而非普通 `-` |
| 观察池基金 | 与持仓基金一样进入 fundgz 估值缓存 |
| 第三批行情异常抛出 | 本次不继续生成信号 |
| 应用启动 | 后台异步预热指数/板块/资金和基金估值；外部接口延迟不阻塞健康检查 |

---

## Good/Base/Bad Cases

- **Good**:交易时段,前端 5s 轮询指数,后端 30s 刷新缓存,用户看到近实时行情
- **Good**:一次指数批量请求同时包含自选指数与沪深京固定市场,两个缓存独立投影
- **Base**:市场宽度首次预热失败,组合收益仍正常展示,进度条为空轨道
- **Good**:15:20 盘后发布重启,异步预热 fundgz 后全仓收益继续显示今日估值
- **Good**:东方财富启动预热超时,应用 readiness 仍可及时完成,缓存等待后台任务或下次定时刷新
- **Good**:某基金本轮超时后旧估值立即消失,总览显示「估值拉取失败」;下一轮成功后自动恢复
- **Good**:14:50 第三批快照完成后才读取快照生成信号
- **Base**:估值接口暂时失败且缓存为空,今日涨跌显示未知而不是昨日值
- **Bad**:fundgz 返回昨日 `gztime`,仍继续作为今日估值使用
- **Bad**:估值缓存已失效,收益服务仍用上一期已公布净值计算当前持仓市值/总盈亏
- **Bad**:今日净值未落库时用最近两期落库净值计算,把昨日收益标成今日收益
- **Bad**:实时任务用上海午夜 Instant 查询 UTC DATE 行,导致交易日永远错位 8 小时
- **Bad**:行情抓取和信号生成使用两个同秒 cron,信号可能先读到缺失快照
- **Bad**:从用户自选指数的 `f104/f105` 相加市场宽度,会因沪深300等成分范围重叠而重复计数

---

## Tests Required

- `EastmoneyJsParserRealtimeTest`:实时行情解析测试覆盖正常响应、空响应、字段缺失
  - 断言点:f2÷100 还原、f3÷100 还原、f6 原值、f62 缺失为 null；北向资金解析仅作为遗留兼容回归
- `EastmoneyJsParserRealtimeTest`:市场宽度断言三个固定市场完整时正确求和；缺市场、缺 `f104/f105` 时返回 null。
- 缓存层降级测试:指数/市场宽度等仍验证旧缓存保留；基金估值必须单独验证成功后空响应、异常、旧日期都会删除旧值。
- `MarketRealtimeRefreshJobTest`:固定 Clock,断言北京时间自然日映射到 UTC 00:00 日历标签。
- `MarketRealtimeCacheTest`:断言持仓与观察池基金都调用 `fetchEstimate`；两个启动事件都带 `@Async`，实时行情事件不查询基金列表，基金估值事件填充估值缓存。
- `MarketRealtimeCacheTest`:固定 `Clock`,断言估值失败立即删除旧缓存并标记失败,旧日期拒收,后续成功清除失败状态。
- `MarketRealtimeCacheTest`:断言一次指数请求同时包含自选与固定市场；残缺响应不覆盖旧 `breadthCache`。
- `DailyChangeResolverTest`:断言今日净值未落库且估值为空时返回未知，不使用 T-1 对 T-2。
- `FundPnlServiceTest`:断言估值失败时当前持仓市值/总盈亏未知且组合失败数正确；当日净值已入库时忽略估值失败状态。
- `MarketDataFetchJobTest`:用 `InOrder` 断言 `fetchBatch(2)` 完成后才生成信号。

---

## Wrong vs Correct

### Wrong:前端直接轮询东方财富

```javascript
// 错误:N 个前端 × 5s 轮询 × 直接调东方财富 = 瞬间超 2 req/s 被封 IP
useQuery({
    queryFn: () => fetch('https://push2.eastmoney.com/...'),
    refetchInterval: 5_000,
});
```

### Correct:前端轮询后端缓存,后端定时刷新

```javascript
// 正确:前端读后端内存缓存,后端 30s 刷一次东方财富
useQuery({
    queryFn: () => get('/api/market/indices/realtime'),
    refetchInterval: 5_000,
});
```

后端 `MarketRealtimeCache` 用 volatile 字段 + `@Scheduled` 30s 刷新,
前端 N 客户端共享同一份缓存,东方财富侧请求量恒定(与客户端数无关)。

### Wrong:盘后重启后等待下一交易时段

```java
@Async
@EventListener(ApplicationReadyEvent.class)
public void onApplicationReady() {
    refreshRealtimeWithoutEstimates();
}

return dailyChangePct(latestNav, previousNav); // T-1 vs T-2 是昨日涨跌
```

### Correct:启动完成后异步预热,缺失时返回未知

```java
@Async
@EventListener(ApplicationReadyEvent.class)
public void warmFundEstimatesAfterReady() {
    refreshFundEstimates();
}

return new DailyChangeResult(null, false); // 不用昨日值冒充今日值
```

### Wrong:基金估值失败沿用通用旧缓存降级

```java
fundEstimateService.fetchEstimate(code)
        .ifPresent(snapshot -> estimateCache.put(code, snapshot));
// empty/异常时旧 snapshot 仍留在 map,下一轮会继续冒充今日估值。
```

### Correct:基金估值按本轮结果替换并校验自然日

```java
FundEstimateSnapshot snapshot = fundEstimateService.fetchEstimate(code).orElse(null);
if (isEstimateForToday(snapshot)) {
    estimateCache.put(code, snapshot);
    estimateFetchFailures.remove(code);
} else {
    estimateCache.remove(code);
    estimateFetchFailures.add(code);
}
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
