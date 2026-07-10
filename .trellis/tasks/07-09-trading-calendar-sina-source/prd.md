# 交易日历换源新浪接口

## Goal

将 `trading_calendar` 表的数据来源从"上证指数 K 线推断"换成"新浪交易日历接口"，让 `isTradingDay` 判定可靠、可前瞻、可自动同步。解决当前从 K 线推断的 6 个问题（非交易日/未同步不可区分、无法前瞻、今天鸡生蛋、同步无调度、数据源可用性当事实、与 cron 口径割裂）。

## Background

### 现状（代码库已确认）

- `TradingCalendarSyncService.sync()`（`market/service/TradingCalendarSyncService.java:41-63`）从上证指数 K 线推断交易日：拉 secid=1.000001 的 10000 根日 K，凡有 K 线的日期标 `tradingDay=true` 插入表。**只插 true 行，从不写 false，从不更新已有行**。
- `TradingCalendarService.isTradingDay`（`fund/service/support/TradingCalendarService.java:23-27`）查表，缺记录返 false（"非交易日"与"未同步"语义混淆）。
- 同步**无 @Scheduled**，只能 `POST /api/admin/market-data/sync-trading-calendar` 手动触发（`AdminMarketDataController.java:32-35`）；换环境忘同步则表空，DCA/信号/实时刷新全失效且无告警。
- 表结构 `trading_calendar(calendar_date, is_trading_day)`（`V3__.sql:30-43`），数据形态是"有记录=交易日"。
- 另有 cron `MON-FRI` 口径（净值确认/行情拉取），与表口径对"调休补班周末"会矛盾。**本期不统一 cron 口径**（用户决策：最小改造）。

### 数据源调研（已实测）

- **东方财富无专门交易日历接口**（实测确认）。
- 新浪 `https://finance.sina.com.cn/realstock/company/klc_td_sh.txt` 返回 `var datelist="LC/AAA..."` 的 KLC 自定义编码，需 `hk_js_decode`（17KB JS，akshare 内嵌）解码为 ISO 日期列表。
- 实测解码结果 8796 条（1990-12-19 ~ 2026-12-31），格式 `1990-12-19T00:00:00.000Z`，仅含交易日。
- 关键验证：调休补班周末（2024-09-29 周日、2024-10-12 周六）正确判为**非交易日**（股市调休补班但休市）。
- akshare 额外手动补了 1992-05-04（新浪数据缺失的一个历史交易日），换源时需同样补上。
- 项目已有 GraalVM JS 引擎（`EastmoneyJsParser.java` 用 `Context.create("js")`），可复用跑 `hk_js_decode`。

## Requirements

### R1 新增新浪交易日历 Client

- 新增 `SinaTradingCalendarClient`（Feign），`GET https://finance.sina.com.cn/realstock/company/klc_td_sh.txt`，返回原始文本。
- base-url 可配置（`EastmoneyClientConfig` 模式，独立 target）。
- 请求头复用共享拦截器（Referer 等）。

### R2 新增解码器

- 新增 `SinaTradingCalendarParser`，用 GraalVM JS 跑 `hk_js_decode` 解码新浪 txt，返回 `List<Instant>`（交易日列表，UTC 0 点）。
- `hk_js_decode.js` 作为资源文件放 `src/main/resources`（从 akshare 提取，17KB）。
- 补 1992-05-04（新浪历史缺失日，akshare 同样补法）。
- 解码失败/空结果抛异常（不静默降级，避免污染日历）。

### R3 改造 TradingCalendarSyncService

- 数据源从 `EastmoneyKlineClient` 换成 `SinaTradingCalendarClient` + `SinaTradingCalendarParser`。
- 同步逻辑：拉新浪交易日列表，对表中新日期 INSERT（`tradingDay=true`），已有日期跳过（幂等）。**保留只插 true 行的数据形态**，与现有 `isTradingDay` 缺记录返 false 的语义一致。
- 移除对 `EastmoneyKlineClient`/`parseIndexKline` 的依赖。

### R4 自动同步调度

- 给 `sync()` 加 `@Scheduled`（每日定时，如凌晨），或新建 `TradingCalendarSyncJob` 调用 `sync()`。
- 频率：每日 1 次足够（新浪数据覆盖到年底，节假日安排前一年 11 月发布）。
- 启动时预热（`@EventListener(ApplicationReadyEvent.class)`），避免新环境表空。
- 同步失败记 warn 不阻断（沿用项目降级模式）。

## Acceptance Criteria

- [ ] AC1 调用 `sync()` 后，`trading_calendar` 表含新浪返回的全部交易日（约 8797 条），含 1992-05-04。
- [ ] AC2 `isTradingDay(2024-09-29)` = false（调休补班周末，股市休市）；`isTradingDay(2024-10-08)` = true（国庆后首个交易日）。
- [ ] AC3 `isTradingDay` 对未来日期（如 2026-12-31）能正确返回 true（数据已覆盖到年底，可前瞻）。
- [ ] AC4 应用启动时自动预热同步一次；每日定时同步；同步失败不阻断启动。
- [ ] AC5 重复 `sync()` 幂等，不产生重复记录，已有日期不被覆盖。
- [ ] AC6 手动 admin 接口 `POST /api/admin/market-data/sync-trading-calendar` 仍可用（换源后调用新浪）。

## Out of Scope

- 统一 cron `MON-FRI` Job 口径到 `trading_calendar` 表（用户决策：最小改造，cron Job 暂不动；它们靠数据驱动兜底，影响小）。已知遗留问题：调休补班周末 cron Job 仍漏跑，记录为后续任务。
- 写入非交易日行（`is_trading_day=false`）：保留"有记录=交易日"形态，避免改 `isTradingDay` 语义和迁移。
- `trading_calendar` 表 schema 变更。
- 前端展示交易日历。

## Open Questions

- 无（数据源、范围、解码方式均已确认）。
