# Design (Technical): 行情工作台后端技术设计

> 本文是 `design.md`（UI/UX）的技术补充。所有类名、字段、SQL 均基于现有代码模式（`market/` 模块 + `user/` 模块）。

## 1. 数据模型扩展

### 1.1 `user_config` 表新增关注指数字段

**Flyway 迁移**：`V7__add_watched_indices_to_user_config.sql`

```sql
-- 用户关注的大盘指数列表（secid 格式，逗号分隔，如 "1.000001,1.000300,0.399006"）
-- 用单字段而非关联表：单用户场景、列表短（通常 3-8 个指数）、无需对指数做关联查询
ALTER TABLE user_config ADD COLUMN watched_indices VARCHAR(512);
```

**理由**：单用户场景 + 指数列表本身是静态字典（无需 JOIN 查指数详情），用 VARCHAR 比关联表简单。最多 ~8 个 secid × ~12 字符 ≈
100 字符，512 足够。

### 1.2 `UserConfigEntity` 扩展

```java
// com.fundpilot.backend.user.entity.UserConfigEntity
@Column(name = "watched_indices", length = 512)
private String watchedIndices;  // 逗号分隔的 secid，null/空 = 用默认列表
```

**默认指数列表常量**（在 `UserConfigService` 中）：

```java
private static final String DEFAULT_WATCHED_INDICES = "1.000001,1.000300,0.399006";
// 上证指数、沪深300、创业板指
```

`get()` 返回时若 `watchedIndices` 为空，填入默认值（不落库，仅 view 层兜底）。

### 1.3 `UserConfigView` 扩展

```java
public record UserConfigView(
    Long id,
    BigDecimal totalInvestableCapital,
    List<String> watchedIndices,  // 新增：解析后的 secid 列表
    Instant createdDate
) {
    public static UserConfigView from(UserConfigEntity config) { ... }
}
```

### 1.4 `UserConfigUpdateRequest` 扩展

```java
public record UserConfigUpdateRequest(
    BigDecimal totalInvestableCapital,
    List<String> watchedIndices  // 新增：可空（不传 = 不修改）
) {}
```

---

## 2. 行情实时数据模型（Records）

新增 records 放在 `com.fundpilot.backend.market.client`（与 `FundNavSnapshot` 等同包）：

```java
// 指数实时行情
public record IndexRealtimeSnapshot(
    String secid,              // "1.000001"
    String name,               // "上证指数"
    BigDecimal currentPrice,   // 当前点位
    BigDecimal changeAmount,   // 涨跌额
    BigDecimal changePct,      // 涨跌幅（小数，+0.0123 = +1.23%）
    Instant snapshotTime,      // 数据时间
    BigDecimal prevClose       // 昨收
) {}

// 行业板块涨跌
public record SectorSnapshot(
    String sectorCode,         // 板块代码
    String sectorName,         // "半导体"
    BigDecimal changePct,      // 今日涨跌幅
    BigDecimal turnover,       // 成交额（元）
    String leadingStockName,   // 领涨股名称（可空）
    BigDecimal leadingStockChangePct  // 领涨股涨跌幅（可空）
) {}

// 遗留北向资金快照；当前工作台使用 SectorSnapshot.mainforceNet 展示行业主力资金
public record MoneyFlowSnapshot(
    BigDecimal northboundNet,   // 北向资金净流入（元，正=流入）
    BigDecimal mainforceNet,    // 主力净流入
    BigDecimal superLargeNet,   // 超大单净流入
    BigDecimal largeNet,        // 大单净流入
    BigDecimal mediumNet,       // 中单净流入
    BigDecimal smallNet,        // 小单净流入
    Instant snapshotTime
) {}

// 基金估值（批量返回，复用现有 FundEstimateSnapshot 但加 fundCode）
// 现有 FundEstimateSnapshot 已有：estimatedChangePct, estimateTime, baseNavDate
// 批量接口返回 Map<String, FundEstimateSnapshot>，key = fundCode
```

---

## 3. Feign 客户端扩展

### 3.1 `EastmoneyPush2Client`（指数实时 + 板块 + 资金流向）

**域名**：`push2.eastmoney.com`（注意是 `push2` 不是 `push2his`，后者是历史数据）

```java
// com.fundpilot.backend.market.client.EastmoneyPush2Client
public interface EastmoneyPush2Client {

    /** 批量指数实时行情（secid 用逗号分隔，最多 ~20 个） */
    @RequestLine("GET /api/qt/ulist.np/get?fields=f1%2Cf2%2Cf3%2Cf4%2Cf5%2Cf6%2Cf12%2Cf14&secids={secids}")
    String fetchIndexRealtimeRaw(@Param("secids") String secids);

    /** 行业板块涨跌排行 */
    @RequestLine("GET /api/qt/clist/get?pn=1&pz=20&po=1&np=1&fields=f12%2Cf14%2Cf3%2Cf6&fs=m:90+t:2")
    String fetchSectorListRaw();

    /** 资金流向（沪深两市） */
    @RequestLine("GET /api/qt/stock/fflow/daykline/get?secid=1.000001&lmt=1")
    String fetchMoneyFlowRaw();
}
```

**字段含义**（东方财富 f 系列字段）：

- `f2` = 最新价，`f3` = 涨跌幅，`f4` = 涨跌额，`f14` = 名称，`f12` = 代码，`f6` = 成交额
- `fs=m:90 t:2` = 行业板块（m:90 是板块，t:2 是行业类）

### 3.2 `EastmoneyClientConfig` 注册新 Bean

```java
@Bean
EastmoneyPush2Client eastmoneyPush2Client(
    @Value("${eastmoney.push2-base-url:https://push2.eastmoney.com}") String baseUrl) {
    return Feign.builder()
        .client(new RateLimitedClient(SHARED_LIMITER))  // 复用共享限流
        .requestInterceptor(requestInterceptor())
        .retryer(retryer())
        .target(EastmoneyPush2Client.class, baseUrl);
}
```

> ⚠️ **API 结构需在实施前先用浏览器/Postman 验证**：东方财富的 push2 接口字段编号（f1, f2, f3...）和 `fs`
> 过滤参数可能随时调整。设计阶段基于公开资料假设，实施时以实际响应为准。

---

## 4. 解析器扩展

在 `EastmoneyJsParser`（static methods）新增：

```java
// 批量指数实时行情（push2 ulist.np 返回标准 JSON）
public static List<IndexRealtimeSnapshot> parseIndexRealtime(String rawJson) {
    // data.diff[] 数组，每个元素 {f2:price, f3:pct, f4:amount, f12:code, f14:name}
    // 用 Jackson（同 parseIndexKline 模式）
}

// 行业板块涨跌（push2 clist 返回标准 JSON）
public static List<SectorSnapshot> parseSectorList(String rawJson) {
    // data.diff[] 数组，同上结构
}

// 资金流向（push2 fflow 返回 JSON）
public static MoneyFlowSnapshot parseMoneyFlow(String rawJson) {
    // data.klines[] 最后一条，CSV 格式 "time,主力,小单,中单,大单,超大单,..."
}
```

**降级策略**：解析失败返回 `null` 或空列表（参考现有 `parseFundGz` 的 graceful degrade 模式），不抛异常中断整个缓存刷新。

---

## 5. 缓存层设计

### 5.1 `MarketRealtimeCache`（内存缓存）

```java
// com.fundpilot.backend.market.service.MarketRealtimeCache
@Service
@RequiredArgsConstructor
@Slf4j
public class MarketRealtimeCache {

    private final EastmoneyPush2Client push2Client;
    private final EastmoneyFundGzClient fundGzClient;
    private final UserConfigService userConfigService;
    private final FundRepository fundRepository;

    // 缓存字段（volatile 或 ConcurrentHashMap，定时任务单线程刷新）
    private volatile List<IndexRealtimeSnapshot> indexCache = List.of();
    private volatile List<SectorSnapshot> sectorCache = List.of();
    private volatile MoneyFlowSnapshot moneyFlowCache;
    private final Map<String, FundEstimateSnapshot> estimateCache = new ConcurrentHashMap<>();

    /** 读取指数缓存（按用户关注列表过滤） */
    public List<IndexRealtimeSnapshot> getIndices() {
        List<String> watched = userConfigService.getWatchedIndices();
        return indexCache.stream()
            .filter(i -> watched.contains(i.secid()))
            .toList();
    }

    public List<SectorSnapshot> getSectors() { return sectorCache; }
    public MoneyFlowSnapshot getMoneyFlow() { return moneyFlowCache; }

    /** 批量基金估值（缺失的返回 null，不阻塞） */
    public Map<String, FundEstimateSnapshot> getEstimates(List<String> fundCodes) {
        // 从 estimateCache 读取，缺失的实时拉取（受限于 2 req/s，分批）
    }

    /** 定时刷新——由 MarketRealtimeRefreshJob 调用 */
    @Transactional(readOnly = true)
    public void refreshAll() {
        refreshIndices();
        refreshSectors();
        refreshMoneyFlow();
        refreshFundEstimates();
    }

    private void refreshIndices() {
        try {
            String secids = String.join(",", userConfigService.getWatchedIndices());
            String raw = push2Client.fetchIndexRealtimeRaw(secids);
            indexCache = EastmoneyJsParser.parseIndexRealtime(raw);
        } catch (RuntimeException e) {
            log.warn("指数实时行情刷新失败，保留旧缓存", e);
        }
    }
    // ... 其他 refresh 方法类似，失败保留旧缓存
}
```

**关键设计**：

- **失败保留旧缓存**（参考 `FundEstimateService.fetchEstimate` 的 catch RuntimeException 模式）
- **不引入 Caffeine/Guava**——用裸 volatile + ConcurrentHashMap，数据本身就是定时刷新的短时态
- **读取零阻塞**——前端轮询只读缓存字段，不触发外部请求

### 5.2 `MarketRealtimeRefreshJob`（定时刷新）

```java
// com.fundpilot.backend.market.job.MarketRealtimeRefreshJob
@Component
@RequiredArgsConstructor
public class MarketRealtimeRefreshJob {

    private final MarketRealtimeCache cache;
    private final TradingCalendarService tradingCalendarService;  // 复用现有交易日判断

    /** 交易时段每 30 秒刷新一次 */
    @Scheduled(cron = "*/30 * 9-14 * * MON-FRI")
    public void refresh() {
        if (!isTradingHours()) return;
        cache.refreshAll();
    }

    private boolean isTradingHours() {
        // 9:30-11:30, 13:00-15:00
        // 用 TradingCalendar 判断是否交易日 + LocalTime 判断时段
    }
}
```

**cron 说明**：`*/30 * 9-14 * * MON-FRI` = 周一至周五 9:00-14:59 每 30 秒。实际交易时段判断在 `isTradingHours()`
内做精细控制（13:00-15:00 的下午段需要覆盖，所以 hours 范围放宽到 9-14，靠 LocalTime 二次过滤）。

> JobMetricsAspect 会自动给这个 `@Scheduled` 方法加监控指标，无需手动埋点。

---

## 6. REST 接口设计

### 6.1 `MarketRealtimeController`

```java
// com.fundpilot.backend.market.controller.MarketRealtimeController
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketRealtimeController {

    private final MarketRealtimeCache cache;

    /** 用户关注指数的实时行情（5s 轮询） */
    @GetMapping("/indices/realtime")
    public ApiResponse<List<IndexRealtimeView>> indices() {
        return ApiResponse.ok(cache.getIndices().stream()
            .map(IndexRealtimeView::from).toList());
    }

    /** 批量基金估值（10s 轮询） */
    @GetMapping("/funds/estimates")
    public ApiResponse<Map<String, FundEstimateView>> estimates(
            @RequestParam("codes") String codes) {
        List<String> codeList = Arrays.asList(codes.split(","));
        Map<String, FundEstimateSnapshot> estimates = cache.getEstimates(codeList);
        return ApiResponse.ok(estimates.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                e -> FundEstimateView.from(e.getValue()))));
    }

    /** 行业板块涨跌排行（30s 轮询） */
    @GetMapping("/sectors")
    public ApiResponse<List<SectorView>> sectors() {
        return ApiResponse.ok(cache.getSectors().stream()
            .map(SectorView::from).toList());
    }

    /** 资金流向（30s 轮询） */
    @GetMapping("/money-flow")
    public ApiResponse<MoneyFlowView> moneyFlow() {
        return ApiResponse.ok(MoneyFlowView.from(cache.getMoneyFlow()));
    }
}
```

### 6.2 View DTOs

```java
// com.fundpilot.backend.market.controller（与 MarketIndicatorSnapshotView 同包）
public record IndexRealtimeView(
    String secid, String name, BigDecimal currentPrice,
    BigDecimal changeAmount, BigDecimal changePct, Instant snapshotTime
) {
    public static IndexRealtimeView from(IndexRealtimeSnapshot s) { ... }
}

public record SectorView(
    String sectorName, BigDecimal changePct, BigDecimal turnover,
    String leadingStockName, BigDecimal leadingStockChangePct
) {
    public static SectorView from(SectorSnapshot s) { ... }
}

public record MoneyFlowView(
    BigDecimal northboundNet, BigDecimal mainforceNet,
    BigDecimal superLargeNet, BigDecimal largeNet,
    BigDecimal mediumNet, BigDecimal smallNet, Instant snapshotTime
) {
    public static MoneyFlowView from(MoneyFlowSnapshot s) { ... }
}

public record FundEstimateView(
    BigDecimal estimatedChangePct, String estimateTime, String baseNavDate
) {
    public static FundEstimateView from(FundEstimateSnapshot s) { ... }
}
```

### 6.3 K线数据接口

```java
// 复用现有 MarketDataController 或新建子路由
@GetMapping("/api/funds/{fundId}/kline")
public ApiResponse<KlineView> fundKline(
        @PathVariable Long fundId,
        @RequestParam(defaultValue = "daily") String period) {  // daily|weekly|monthly
    // 1. 查 FundEntity 获取 fundSubType + benchmarkIndexCode
    // 2. ETF/INDEX/INDEX_ENHANCED → 用 benchmarkIndexCode 拉 IndexKline（period 决定 klt 参数：101/102/103）
    // 3. ACTIVE/MIXED → 拉基金净值历史，转成走势数据
    // 4. 按需拉取（不缓存），K线数据量大且用户主动查看
}
```

**period → klt 映射**（东方财富参数）：

- `daily` → `klt=101`
- `weekly` → `klt=102`
- `monthly` → `klt=103`

需扩展 `EastmoneyKlineClient` 支持参数化 `klt`（当前硬编码 `klt=101`）：

```java
@RequestLine("GET /api/qt/stock/kline/get?secid={secid}&fields1=...&fields2=...&klt={klt}&fqt=1&beg=0&end=20500101&lmt={lmt}")
String fetchKlineRaw(@Param("secid") String secid, @Param("klt") String klt, @Param("lmt") String lmt);
```

---

## 7. `UserConfigService` 扩展

```java
// 新增方法
public List<String> getWatchedIndices() {
    UserConfigEntity config = requireConfig();
    String raw = config.getWatchedIndices();
    if (raw == null || raw.isBlank()) {
        return Arrays.stream(DEFAULT_WATCHED_INDICES.split(",")).toList();
    }
    return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
}

// 修改 update 方法签名
@Transactional
public UserConfigView update(BigDecimal totalInvestableCapital, List<String> watchedIndices) {
    UserConfigEntity config = requireConfig();
    if (totalInvestableCapital != null) {
        config.setTotalInvestableCapital(totalInvestableCapital);
    }
    if (watchedIndices != null) {
        config.setWatchedIndices(String.join(",", watchedIndices));
    }
    userConfigRepository.save(config);
    return UserConfigView.from(config);
}
```

---

## 8. 文件清单（新增/修改）

### 新增文件

```
backend/src/main/java/com/fundpilot/backend/market/
├── client/
│   ├── EastmoneyPush2Client.java           ← 新 Feign 接口
│   ├── IndexRealtimeSnapshot.java          ← record
│   ├── SectorSnapshot.java                 ← record
│   └── MoneyFlowSnapshot.java              ← record
├── service/
│   └── MarketRealtimeCache.java            ← 内存缓存服务
├── job/
│   └── MarketRealtimeRefreshJob.java       ← 定时刷新
└── controller/
    ├── MarketRealtimeController.java       ← 4 个新接口
    ├── IndexRealtimeView.java              ← record DTO
    ├── SectorView.java                     ← record DTO
    ├── MoneyFlowView.java                  ← record DTO
    └── FundEstimateView.java               ← record DTO

backend/src/main/resources/db/migration/
└── V7__add_watched_indices_to_user_config.sql

backend/src/test/java/com/fundpilot/backend/market/
├── client/
│   ├── EastmoneyPush2ClientTest.java       ← MockWebServer 测试
│   └── EastmoneyJsParserRealtimeTest.java  ← 解析器测试
└── service/
    └── MarketRealtimeCacheTest.java        ← 缓存读写测试
```

### 修改文件

```
backend/
├── market/client/EastmoneyClientConfig.java       ← 注册 push2Client Bean
├── market/client/EastmoneyKlineClient.java        ← klt 参数化
├── market/client/EastmoneyJsParser.java           ← 3 个新 parse 方法
├── market/client/EastmoneyMarketDataSource.java   ← fetchIndexKline 传 klt
├── market/service/MarketDataFetchService.java     ← 调用处适配 klt 参数
├── user/entity/UserConfigEntity.java              ← 新增 watchedIndices 字段
├── user/controller/UserConfigView.java            ← 新增字段
├── user/controller/UserConfigController.java      ← update 请求体扩展
└── user/service/UserConfigService.java            ← getWatchedIndices + update 扩展
```

---

## 9. 限流与性能考量

### 9.1 东方财富 2 req/s 限制下的刷新策略

| 数据类型 | 刷新频率 | 请求数/次           | 每日请求估算          |
|----------|----------|---------------------|-----------------------|
| 指数实时 | 30s      | 1（批量）           | ~600（4小时交易时段） |
| 板块涨跌 | 30s      | 1                   | ~600                  |
| 资金流向 | 30s      | 1                   | ~600                  |
| 基金估值 | 30s      | N（逐个，N=基金数） | ~600×N                |

**基金估值是瓶颈**：若用户有 10 只基金，30s 一轮 = 10 个请求 / 30s = 0.33 req/s，可接受。但若 30+ 只基金，需要：

- 方案A：降低基金估值刷新频率到 60s（指数/板块仍 30s）
- 方案B：分片拉取，每 30s 拉一半基金，1 分钟轮完一轮

**本期采用方案A**：基金估值 60s 刷新，指数/板块/资金 30s 刷新。前端 5-10s 轮询时，估值数据最多滞后 60s（可接受）。

### 9.2 前端轮询不击穿到外部

前端 5-10s 轮询 → 命中 `MarketRealtimeCache` 内存 → **零外部请求**。只有 `@Scheduled` 的 30s/60s 任务才触发东方财富请求。N
个前端客户端共享同一份缓存。

---

## 10. 待实施前验证的接口

以下东方财富 API 的具体字段结构需在实施阶段第一步验证（写个临时 main 方法或 Postman 测）：

1. **`push2.eastmoney.com/api/qt/ulist.np/get`** — 批量指数实时，确认 `fields` 参数和 `secids` 格式
2. **`push2.eastmoney.com/api/qt/clist/get`** — 板块列表，确认 `fs=m:90 t:2` 是否仍是行业板块
3. **`push2.eastmoney.com/api/qt/stock/fflow/daykline/get`** — 资金流向，确认返回结构和字段顺序

**降级预案**：若任一接口结构不符预期或不可用，对应功能返回空列表/null，前端显示「数据暂不可用」，不影响其他模块。
