# 实施计划：交易日历换源新浪接口

## 有序清单

### 1. 新增 `SinaTradingCalendarClient`（Feign 接口）

- 文件：`backend/src/main/java/com/fundpilot/backend/market/client/SinaTradingCalendarClient.java`
- `@RequestLine("GET /realstock/company/klc_td_sh.txt")` 返回 `String`。
- 参照 `EastmoneyKlineClient` 模式（纯接口，无 @FeignClient 注解，由 Config 注册 bean）。

### 2. 在 `EastmoneyClientConfig` 注册新浪 client bean

- 新增
  `@Bean SinaTradingCalendarClient sinaTradingCalendarClient(@Value("${sina.base-url:https://finance.sina.com.cn}") String baseUrl)`。
- Feign.builder () + RateLimitedClient (SHARED_LIMITER) + requestInterceptor () + retryer () + target。

### 3. 新增 `SinaTradingCalendarParser`（GraalVM JS 解码）

- 文件：`backend/src/main/java/com/fundpilot/backend/market/client/SinaTradingCalendarParser.java`
- `static List<Instant> parse(String rawText)`：
    - 提取 payload：`rawText.split("=")[1].split(";")[0].replace("\"", "")`。
    - `Context.create("js")` eval `hk_js_decode.js`（从 classpath 读 `Resource`），call `d(payload)`。
    - 遍历返回数组，`Instant.parse("1990-12-19T00:00:00.000Z")` 转 Instant。
    - 补 1992-05-04（`LocalDate.of(1992,5,4).atStartOfDay(UTC).toInstant()`），若不在列表中。
    - 空结果抛 `IllegalStateException`（不降级）。
- 资源文件 `hk_js_decode.js` 已就位 `src/main/resources/`。
- 参照 `EastmoneyJsParser.parseNavHistory` 的 GraalVM 用法。

### 4. 改造 `TradingCalendarSyncService.sync()`

- 文件：`backend/src/main/java/com/fundpilot/backend/market/service/TradingCalendarSyncService.java`
- 依赖从 `EastmoneyKlineClient` + `EastmoneyJsParser` 换成 `SinaTradingCalendarClient` + `SinaTradingCalendarParser`。
- `sync()`：`raw = sinaClient.fetchTradingCalendarRaw()` -> `dates = SinaTradingCalendarParser.parse(raw)` -> 对表中新日期
  INSERT。
- 移除 `SH_COMPOSITE`/`LMT` 常量和 K 线相关代码。
- 类注释更新为"从新浪交易日历接口同步"。

### 5. 新增 `TradingCalendarSyncJob`（@Scheduled + 预热）

- 文件：`backend/src/main/java/com/fundpilot/backend/market/job/TradingCalendarSyncJob.java`
- 参照 `FundFeeRefreshJob` 模式。
- `@Scheduled(cron = "0 0 4 * * *")` 每日 04:00。
- `@EventListener(ApplicationReadyEvent.class)` 启动预热。
- try-catch 调 `sync()`，失败记 warn 不阻断。

### 6. 更新测试

- `TradingCalendarServiceTest`（若有 sync 测试）：mock 改为 `SinaTradingCalendarClient` + parser。
- 新增 `SinaTradingCalendarParserTest`：用真实新浪响应样例（或 akshare 解码结果）验证解码正确，含 1992-05-04。
- 保留 `TradingCalendarSchemaIntegrationTest` 不动（表结构未变）。

### 7. 更新 `AdminMarketDataController` 注释

- L31 注释"从东方财富同步"改为"从新浪同步"（仅注释，逻辑不动）。

## 验证命令

- 后端：`cd backend && ./mvnw test`（聚焦：
  `-Dtest=TradingCalendarServiceTest,SinaTradingCalendarParserTest,TradingCalendarSchemaIntegrationTest`）
- 手动验证：`POST /api/admin/market-data/sync-trading-calendar` 后查 `trading_calendar` 表条数（应约 8797）。
- 本机 JDK 25 限制：同上个任务，需在 JDK 25 环境跑。

## 高风险文件 / 回滚点

| 文件                         | 风险                         | 回滚                                                     |
|------------------------------|------------------------------|----------------------------------------------------------|
| `SinaTradingCalendarParser`  | GraalVM JS 解码失败/格式不符 | 解码失败抛异常，Job 层 catch 保留旧数据；回退到 K 线推断 |
| `TradingCalendarSyncService` | 换源后字段遗漏导致编译错     | 单点改动，回退即恢复                                     |
| `hk_js_decode.js`            | 与新浪协议版本不匹配         | 资源文件可单独替换                                       |

## 实施前检查

- [x] `hk_js_decode.js` 已提取到 `src/main/resources/`（17802 bytes）。
- [x] 新浪接口实测可达，解码结果 8796 条 + 补 1992-05-04。
- [x] GraalVM JS 引擎项目已有（`EastmoneyJsParser`）。
- [ ] 实施前运行 `trellis-before-dev` 注入 spec（market 层）。
