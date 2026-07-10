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
GET /api/market/funds/estimates?codes=xxx,yyy → Map<String, FundEstimateView>
GET /api/market/sectors              → List<SectorView>
GET /api/market/money-flow           → MoneyFlowView
GET /api/funds/{fundId}/kline?period=daily|weekly|monthly → KlineView
```

### 缓存服务(`MarketRealtimeCache`)

```java
public List<IndexRealtimeSnapshot> getIndices();        // 读 volatile 字段
public List<SectorSnapshot> getSectors();
public MoneyFlowSnapshot getMoneyFlow();
public Map<String, FundEstimateSnapshot> getEstimates(List<String> codes); // 只读缓存,不拉外部接口
public void refreshAll();                               // 全量刷新(含估值)
public void refreshRealtimeWithoutEstimates();          // 仅刷新指数/板块/资金
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
| 北向资金 | 30s | 30s | 单请求 |
| 基金估值 | **30s** | 10s | 盘中实时性优先,后台逐只刷新并保留旧缓存 |

**关键不变量**:前端轮询频率 > 后端刷新频率。前端读内存零外部请求,
N 个前端客户端共享同一份缓存。

### 东方财富字段缩放契约

| 字段类型 | 字段 | 缩放 | 示例 |
|---------|------|------|------|
| 价格类 | f2, f4 | ÷100 | f2=404364 → 4043.64 |
| 百分比类 | f3 | ÷100 | f3=37 → 0.37% |
| 金额类 | f5, f6, f62, f66, f72, f78, f84 | 原值(元) | f6=1465563104853.7 |

**陷阱**:f2/f3/f4 必须在解析器里 ÷100 还原,f6/f62 等金额字段原值。
混用会导致指数点位差 100 倍或涨跌幅放大 100 倍。

### 交易时段判断契约

- 时区:`Asia/Shanghai`
- 时段:9:30-11:30(上午)、13:00-15:00(下午)
- 交易日查询参数必须使用 `ChinaTradingDate.toUtcDate(clock.instant())`，即北京时间自然日对应的 UTC 00:00 标签
- 非交易日:`trading_calendar` 表无记录或 `is_trading_day=false` 时不刷新
- 非交易时段:Job 的 cron 放宽到 9-14 点,靠 `isTradingHours()` 精细过滤

### 基金估值范围契约

- `refreshFundEstimates()` 遍历 `FundRepository.findAll()`，覆盖全部未软删基金。
- `HOLDING` 与 `PENDING_HOLDING` 都必须进入估值缓存；观察池基金也展示盘中三态涨跌。
- 单只基金拉取失败只跳过本轮该基金，不能中断其他基金，也不能清空旧缓存。
- `ApplicationReadyEvent` 只调用 `refreshRealtimeWithoutEstimates()`；不得在启动线程按基金数执行 fundgz 请求。

### 14:50 串行契约

- 14:30/14:40 仅执行 `fetchBatch(0/1)`。
- 14:50 的唯一调度入口先执行 `fetchBatch(2)`，返回后再调用 `SignalGenerationJob.generateDaily()`。
- `SignalGenerationJob` 不得再声明独立的同秒 `@Scheduled`，手动行情刷新和手动信号生成入口仍独立可用。

---

## Validation & Error Matrix

| 条件 | 行为 |
|------|------|
| 东方财富接口超时/失败 | 保留旧缓存 + 记 warn(不抛异常) |
| 单只基金估值拉取失败 | 跳过该只,不影响其他 |
| 缓存为空(首次启动/全失败) | 返回空列表/null,前端显示「暂无数据」 |
| 用户未配置 watchedIndices | 返默认列表(上证+沪深300+创业板),不抛错 |
| 非交易时段请求 | 返回最后一次缓存数据(可能是上一交易日) |
| 观察池基金 | 与持仓基金一样进入 fundgz 估值缓存 |
| 第三批行情异常抛出 | 本次不继续生成信号 |
| 应用启动 | 只预热指数/板块/资金；基金估值等待交易时段周期任务 |

---

## Good/Base/Bad Cases

- **Good**:交易时段,前端 5s 轮询指数,后端 30s 刷新缓存,用户看到近实时行情
- **Good**:14:50 第三批快照完成后才读取快照生成信号
- **Base**:非交易时段,前端轮询命中旧缓存,显示上一交易日收盘数据
- **Bad**:实时任务用上海午夜 Instant 查询 UTC DATE 行,导致交易日永远错位 8 小时
- **Bad**:行情抓取和信号生成使用两个同秒 cron,信号可能先读到缺失快照

---

## Tests Required

- `EastmoneyJsParserRealtimeTest`:7 个测试覆盖三接口解析(正常响应、空响应、字段缺失)
  - 断言点:f2÷100 还原、f3÷100 还原、f6 原值、f62 缺失为 null、北向资金取 s2n 最后一条
- 缓存层降级测试:mock push2Client 抛异常,验证旧缓存保留(本期未补,留 follow-up)
- `MarketRealtimeRefreshJobTest`:固定 Clock,断言北京时间自然日映射到 UTC 00:00 日历标签。
- `MarketRealtimeCacheTest`:断言持仓与观察池基金都调用 `fetchEstimate`，且启动事件不查询基金列表、不调用单基金估值接口。
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

---

## Design Decision: 资金流向只做北向资金

**Context**:设计阶段设想展示「北向 + 主力 + 超大单 + 大单 + 中单 + 小单」六项全市场汇总。

**验证发现**:东方财富全市场资金汇总接口(`fflow/daykline/get`)返回 `rc:102 data:null`,
结构不稳定。板块级资金(`clist` 的 f62 主力净流入)可靠。

**Decision**:本期 MoneyFlow 只含北向资金一项(来自 `kamt.rtmin` 接口,稳定)。
板块级主力资金随 `SectorSnapshot.mainforceNet` 返回,前端在板块组件里展示。
全市场主力/超大单等汇总留 follow-up,待找到稳定接口再做。

**Extensibility**:`MoneyFlowSnapshot` 是 record,未来加字段只需扩 record + 解析器,
不影响现有 API 契约(新字段对旧前端透明)。
