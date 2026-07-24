# 设计：交易日历换源新浪接口

## 架构与边界

无 schema 变更。改造 `TradingCalendarSyncService` 的数据来源，新增新浪 Feign client + GraalVM JS 解码器 + 自动同步
Job。表结构、`TradingCalendarService`、所有 `isTradingDay` 调用方均不动。

改动面：

- 新增：`SinaTradingCalendarClient`（Feign）、`SinaTradingCalendarParser`（GraalVM JS 解码）、`hk_js_decode.js`（资源文件）、
  `TradingCalendarSyncJob`（@Scheduled + 预热）。
- 改造：`TradingCalendarSyncService.sync()`（换源）、`EastmoneyClientConfig`（注册新浪 client bean）。
- 不动：`TradingCalendarEntity`、`TradingCalendarRepository`、`TradingCalendarService`、`AdminMarketDataController`、所有调用方。

## 数据流与契约

### 同步流程

```
TradingCalendarSyncJob(@Scheduled 每日 + ApplicationReadyEvent 预热)
  -> TradingCalendarSyncService.sync()
    -> SinaTradingCalendarClient.fetchTradingCalendarRaw()   // GET klc_td_sh.txt
    -> SinaTradingCalendarParser.parse(rawText)              // GraalVM JS 跑 hk_js_decode
         -> List<Instant> 交易日(UTC 0 点),补 1992-05-04
    -> loadExistingDates()                                    // 表中已有日期
    -> for each 新日期: INSERT TradingCalendarEntity(tradingDay=true)
    -> return 新增条数
```

### 新浪接口契约

- URL: `https://finance.sina.com.cn/realstock/company/klc_td_sh.txt`
- 响应: `var datelist="LC/AAA...编码字符串...";var KLC_TD_SH=datelist;`
- 解码: GraalVM `Context.create("js")` eval `hk_js_decode.js`，call `d(payload)` 返回 JS 数组，每元素
  `"1990-12-19T00:00:00.000Z"` 格式字符串。
- payload 提取: `rawText.split("=")[1].split(";")[0].replace("\"", "")`。
- 结果约 8796 条，补 1992-05-04 后 8797 条。

### 数据形态（不变）

- `trading_calendar` 表只存"交易日"行（`is_trading_day=true`），非交易日无记录。
- `isTradingDay(date)`：查表有记录返 true，无记录返 false。 **与现状语义一致**，换源后只是数据更全更准。
- 幂等：已有日期跳过，不更新不重复插。

## 关键设计决策

### D1 复用 GraalVM JS 跑 hk_js_decode（而非 Java 重写）

- `hk_js_decode` 是 17KB 复杂位流解码 JS（含 Base64 变种 + 多函数嵌套），Java 重写风险高易错。
- 项目已有 GraalVM JS 引擎（`EastmoneyJsParser`），`Context.create("js")` 模式成熟。
- `hk_js_decode.js` 作为资源文件打包，启动时加载一次（或每次同步加载，开销可接受：每日1次）。
- 解码失败抛异常不降级（避免污染日历；同步失败由 Job 层 catch 记 warn 不阻断）。

### D2 新浪 client 独立 Feign target，共享限流桶

- 新浪域名 `finance.sina.com.cn` 与东方财富不同，独立 target（参照 CsindexClient 模式）。
- 共享 `EastmoneyClientConfig.SHARED_LIMITER` 令牌桶（2 req/s）--虽然新浪不限流，但同步每日1次，共享无害且简化配置。
- Referer 用东方财富的（新浪不校验 Referer，复用拦截器减少特例）。

### D3 自动同步 Job（@Scheduled + 预热）

- 新建 `TradingCalendarSyncJob`（参照 `FundFeeRefreshJob` 模式：`@Scheduled` +
  `@EventListener(ApplicationReadyEvent.class)`）。
- cron: 每日凌晨 `0 0 4 * * *`（04:00，避开 NavConfirmJob 03:00 和其他 Job）。
- 预热: 启动时同步一次，确保新环境/重启后表非空。
- 失败: try-catch 记 warn 不阻断启动/其他 Job。

### D4 不统一 cron MON-FRI 口径（Out of Scope）

- 净值确认/行情拉取等 cron Job 继续用 `MON-FRI`，不改读 `trading_calendar` 表。
- 已知遗留：调休补班周末 cron Job 漏跑。记录为后续任务，本期不处理（用户决策：最小改造）。

## 兼容性与回归

- `AdminMarketDataController.syncTradingCalendar` 不改：仍调 `tradingCalendarSyncService.sync()`，换源后透明生效。
- `TradingCalendarService.isTradingDay` / `daysBetweenTradingDays` 不改：表形态不变。
- `TradingCalendarSyncServiceTest` / `TradingCalendarSchemaIntegrationTest` 需更新：原测试 mock `EastmoneyKlineClient`，改为
  mock `SinaTradingCalendarClient` + `SinaTradingCalendarParser`。
- 历史 K 线推断写入的数据保留（都是真交易日，无脏数据），换源后增量补充新浪多出的日期。

## 风险与回滚

| 风险                                              | 缓解                                                       |
|---------------------------------------------------|------------------------------------------------------------|
| `hk_js_decode.js` 与 akshare 版本绑定，新浪改协议 | JS 作为资源文件可单独更新；解码失败抛异常不污染            |
| GraalVM JS 加载 17KB JS 性能                      | 每日1次同步，可接受；可在 parser 缓存 Context              |
| 新浪接口不可达                                    | Job 层 catch 记 warn，表保留旧数据（与现状"同步失败"等价） |
| 1992-05-04 补丁遗漏                               | 测试断言该日期存在                                         |

回滚点：`TradingCalendarSyncService` 换源是单点改动，回退即恢复 K 线推断。新增的 client/parser/job 可独立删除。
