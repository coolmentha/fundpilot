# 基金字典落库缓存，支持搜索框自动补全

新建基金改为"搜索框 + 自动补全"交互：用户输代码或名称，下拉候选列表，选中后 code/name/fundSubType/
fundCategory/benchmarkIndexCode 一次性回填。字典数据来自东方财富 `fundcode_search.js`（全量约 2 万条），
搜索框每次按键都现拉外部接口会撞东方财富限流（约 2-3 次/秒）。本 ADR 决定字典的缓存方式。

## Considered Options

- **A. 落库缓存 + 定时刷新（已采纳）**：新增 `fund_dict` 表，首次查询或定时任务拉全量字典落库，搜索框查本地表。
  落库时同步跑 fundSubType + fundCategory 识别并缓存识别结果，搜索返回的候选自带分类。
- **B. 进程内缓存（启动加载）**：应用启动时拉一次字典存内存 `ConcurrentHashMap`，进程内检索。
- **C. 防抖 + 短时缓存（最小改动）**：搜索框前端防抖 800ms，后端用 Caffeine/Guava 缓存字典 10 分钟。

## Consequences

选 A 的核心理由：**搜索是高频交互，外部接口是稀缺资源**。

1. 毫秒级响应、永不撞限流——搜索框每次按键查本地表，零外部依赖。
2. 落库时预算 fundSubType + fundCategory 并缓存，避免运行时对同一条目重复跑启发式识别。
3. 多实例一致——所有后端实例读同一张表，无内存不一致问题。
4. 重启不丢——进程内缓存重启需重拉全量字典，落库则数据持久。

代价是多一张表 + 一个定时同步任务 + 迁移脚本。但 `fund_dict` 本质是外部字典的本地镜像，写入只发生在
同步任务，读多写少，PostgreSQL 完全胜任，不需要 Redis（CONTEXT.md 已明确本期不引入 Redis）。

## 与现有架构的关系

`MarketDataFetchService` 已有分批拉取 + 落 `market_indicator_snapshot` 表的模式（14:30/14:40/14:50 三批）。
`fund_dict` 同步沿用同一思路：定时拉全量字典 upsert 到 `fund_dict` 表，与行情指标快照表并列。
`FundDictBackfillService.backfillAll()` 的批量回填职责被取代——建基金时不再调它，改由搜索选中候选时
直接落已识别字段；`backfillAll` 保留给历史遗留 fund 行的补识别（运维手动触发）。

## 表结构要点

`fund_dict` 表：`fund_code`（主键）+ `fund_name` + `raw_name` + `fund_sub_type` + `fund_category` +
`benchmark_index_code` + 审计字段。`fund_sub_type`/`fund_category`/`benchmark_index_code` 在落库时由
`FundSubTypeClassifier` + 新的 `fundCategory` 识别逻辑算出并缓存，搜索接口直接返回缓存值。
唯一约束 `fund_code`，upsert 按 code 合并。
