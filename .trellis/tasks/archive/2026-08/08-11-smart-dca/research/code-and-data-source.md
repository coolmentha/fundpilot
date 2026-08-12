# Research: 智能定投代码与行情数据源

- Query: 为 FundPilot 增加类支付宝的智能定投，核对固定金额、低估、均线、涨跌幅策略的现有代码边界，以及低估策略所需指数 PE 历史分位数据源。
- Scope: mixed
- Date: 2026-08-11

## Findings

### 需求与规则基线

- [`prd.md`](../prd.md) 已固定三种智能策略：低估、均线、涨跌幅，并保留固定金额模式（`prd.md:15-18`）。规则采用本地版本化的 2025-06 支付宝 App 规则快照，不在运行时抓取支付宝或依赖支付宝内部接口（`prd.md:18`, `prd.md:46-50`, `prd.md:57`）。
- 低估策略的当前约定是指数历史 PE 分位 <= 30% 执行基础金额，正常/高估跳过；缺少上一交易日时取最近可用估值并展示实际估值日期（`prd.md:19`, `prd.md:23`）。均线策略默认基金基准指数、250 日，可选 180/250/500 日，金额范围为基础金额的 60%-210%（`prd.md:20-21`）。涨跌幅策略使用最新基金净值与平均持仓成本，金额范围为 50%-200%（`prd.md:22`）。
- 支付宝截图证据对均线示例存在差异：一页以 500 日为例，另一页显示 250 日推荐并出现 180 日选项。当前产品决策已明确固定为 180/250/500 可选、默认 250，不应把单张示例图误当成唯一算法参数。
- 数据不足时必须跳过并记录可查询原因，不能静默降级为固定金额；生成的 `INVEST/PENDING` 只是内部流水，不代表外部真实扣款（`prd.md:9-10`, `prd.md:24`, `prd.md:39`, `prd.md:44`）。

### 现有后端执行链

文件与职责：

- [`InvestmentPlan.java`](../../../../backend/src/main/java/com/fundpilot/backend/investmentplan/domain/investmentplan/InvestmentPlan.java) 是定投规则聚合，当前字段只有计划、频率、日期、启用状态和 `amount`（`InvestmentPlan.java:9-19`）；创建/更新只校验正金额和定投日（`InvestmentPlan.java:54-70`, `InvestmentPlan.java:159-203`）。
- [`InvestmentPlanCommandHandler.java`](../../../../backend/src/main/java/com/fundpilot/backend/investmentplan/application/command/planmanagement/InvestmentPlanCommandHandler.java) 的 `PlanInput` 只接收 `enabled/amount/frequency/dayOfWeek/dayOfMonth`（`InvestmentPlanCommandHandler.java:120-124`），同一组合基金最多保留一个 `EFFECTIVE` 计划（`InvestmentPlanCommandHandler.java:30-43`, `InvestmentPlanCommandHandler.java:58-64`）。智能策略配置必须沿用这一命令边界，而不是在 Controller 中自行拼装。
- [`InvestmentPlanExecutionCommandHandler.java`](../../../../backend/src/main/java/com/fundpilot/backend/investmentplan/application/command/planexecution/InvestmentPlanExecutionCommandHandler.java) 先取北京时间业务日、交易日历、仍为 `TRACKED` 的组合基金和当月幂等状态，再调用 `transactions.createPending`（`InvestmentPlanExecutionCommandHandler.java:24-36`）。当前传入的是 `plan.amount()`，因此动态金额计算应放在该用例的应用服务/策略边界，并把实际金额在创建流水时固定下来。
- [`InvestmentPlanExecutionJob.java`](../../../../backend/src/main/java/com/fundpilot/backend/investmentplan/adapter/scheduler/planexecution/InvestmentPlanExecutionJob.java) 在工作日北京时间 14:55 遍历生效且启用的计划，逐计划隔离异常（`InvestmentPlanExecutionJob.java:12-31`）。行情读取不应放入 Controller 或前端轮询路径。
- [`PlanTransactionGatewayImpl.java`](../../../../backend/src/main/java/com/fundpilot/backend/investmentplan/infrastructure/gateway/planexecution/PlanTransactionGatewayImpl.java) 通过 [`TransactionApi.java`](../../../../backend/src/main/java/com/fundpilot/backend/accounting/adapter/api/transaction/TransactionApi.java) 生成 `INVEST/PENDING`；Accounting 以 `INVESTMENT_PLAN_ALREADY_EXECUTED` 转换为执行幂等结果（`PlanTransactionGatewayImpl.java:25-37`）。
- [`V42__add_investment_plan.sql`](../../../../backend/src/main/resources/db/migration/V42__add_investment_plan.sql) 已有同一计划、同一北京时间自然日的唯一索引（`V42__add_investment_plan.sql:68-77`）；旧 DCA 链也有对应的 `dca_plan_id` 索引（`V17__nav_accounting_rebuild.sql:17-47`）。该约束可继续兜底交易幂等，但无法记录“策略跳过”的原因，因为跳过没有交易行。
- [`InvestmentPlanBudgetSummaryQueryHandler.java`](../../../../backend/src/main/java/com/fundpilot/backend/investmentplan/application/query/budgetmanagement/InvestmentPlanBudgetSummaryQueryHandler.java) 当前按 `plan.amount() * 剩余次数` 计算未来金额（`InvestmentPlanBudgetSummaryQueryHandler.java:58-78`）。这与需求中“预算按基础金额估算、实际以执行日为准”一致，但智能计划需要额外返回区间/说明，不能把区间误当成已承诺流水。

现有可复用事实查询：

- [`PositionApi.java`](../../../../backend/src/main/java/com/fundpilot/backend/accounting/adapter/api/position/PositionApi.java) 已暴露 `costPerShare` 与 `confirmedShares`（`PositionApi.java:216-237`），可用于涨跌幅策略；需要注意零持仓、成本缺失和未确认交易的不可用状态。
- [`FundProductApi.java`](../../../../backend/src/main/java/com/fundpilot/backend/productcatalog/adapter/api/product/FundProductApi.java) 已暴露并可更新 `benchmarkIndexCode`（`FundProductApi.java:263-297`），可作为均线默认参考指数和可选覆盖值。
- [`PublishedNavApi.java`](../../../../backend/src/main/java/com/fundpilot/backend/marketdata/adapter/api/publishednav/PublishedNavApi.java) 有最新、最近两期和历史基金净值查询（`PublishedNavApi.java:27-61`）。
- [`IndexKlineApi.java`](../../../../backend/src/main/java/com/fundpilot/backend/marketdata/adapter/api/indexkline/IndexKlineApi.java) 有本地 `index_kline` 的全量查询和 upsert（`IndexKlineApi.java:73-82`）；[`KlineQueryHandler.java`](../../../../backend/src/main/java/com/fundpilot/backend/marketdata/application/query/klinequery/KlineQueryHandler.java) 已按基准指数优先读取缓存、缓存空时再走行情源（`KlineQueryHandler.java:47-61`），均线可复用该缓存边界。

### 前端现状

- [`DcaPlanFormModal.jsx`](../../../../frontend/src/pages/DcaPlanFormModal.jsx) 目前只渲染启用、每次金额、频率和日期（`DcaPlanFormModal.jsx:18-60`），还没有固定/智能模式、策略、参考指数、均线周期或范围说明。
- [`DcaManagementPage.jsx`](../../../../frontend/src/pages/DcaManagementPage.jsx) 目前按固定 `plan.amount` 展示每次金额和本月剩余预计（`DcaManagementPage.jsx:111-146`），预算组件在页面入口统一读取（`DcaManagementPage.jsx:38-43`, `DcaManagementPage.jsx:173-176`）。
- [`hooks.js`](../../../../frontend/src/api/hooks.js) 已有 `/api/investment-plans`、创建/更新/动作和 `/api/investment-plan-budget/summary` 的 React Query 封装（`hooks.js:145-225`）。智能策略的新增字段应随现有计划 DTO 传输，成功后继续复用定投和预算查询失效逻辑。

### 低估数据源调查

现有行情抽象没有 PE 能力：[`MarketDataSource.java`](../../../../backend/src/main/java/com/fundpilot/backend/marketdata/infrastructure/remote/marketfeed/MarketDataSource.java) 只定义基金净值历史、基金字典和指数 K 线（`MarketDataSource.java:88-108`）；对 `backend/src/main/java/com/fundpilot/backend/market` 及对应测试搜索 `PE/市盈率/ttmPe/peQuantile` 没有匹配。因此不能把现有实时估值 Redis 或 K 线字段当作历史 PE 分位。

已验证的候选公开源是乐咕乐股（仅作为数据源研究，不代表已授权集成）：

- 页面：<https://www.legulegu.com/stockdata/hs300-ttm-lyr>；自定义指数页面：<https://www.legulegu.com/stockdata/index-ttm-lyr-pe?indexCode=930050.CSI>。2026-08-11 使用普通浏览器 `User-Agent` 均返回 200。
- 页面展示沪深 300 当前静态/滚动 PE、统计说明和免责声明。页面说明的计算口径包括：静态 PE = 总市值/年度报告净利润，滚动 PE = 总市值/滚动净利润，并称等权/中位数统计会剔除亏损、暂停上市和 PE > 300 的上市公司。页面同时声明数据不构成证券或交易策略建议，对数据不准确、不完整或依赖数据造成的损失不承担法律责任（页面抓取位置约为 HTML 字符 45205-46600，页面版本日期为 2026-08-11）。
- 页面脚本版本：`/static/js/lg-charts-index-basic-pe.js?date=20260810`；页面加载的新版打包脚本 `/static/js/lg-charts-new-index-basic-pe.js?date=20260810` 包含接口和 token 逻辑。脚本将 `indexCode` 从 URL 查询参数读出，请求 `/api/stockdata/index-basic-pe?indexCode=<code>&token=<token>`。
- token 不是固定密钥，而是脚本中的 `MD5(yyyy-MM-dd)`（`new Hashes.MD5().hex(formatDate(new Date()))`）。2026-08-11 的实测 token 为 `ecf9a4884331ef77f77724c3162f9953`。该值随日期变化，不能作为项目长期凭据。
- API 必须先建立页面会话：直接请求接口即使返回 HTTP 200，`Content-Length` 仍为 0；先 GET 页面取得 `acw_tc/LAAA/JSESSIONID` 等会话 cookie，再请求 API 才返回 JSON。该行为意味着后端若使用它，需要维护短期会话、每日 token、超时和空响应降级。
- 2026-08-11 的会话实测响应结构为 `data,vip,user,dataStartDate,swLevel1,swLevel2,sw`。每个 `data` 元素有 `date`、`lyrPe`、`lyrPeQuantile`、`ttmPe`、`ttmPeQuantile`、`addLyrPe`、`addLyrPeQuantile`、`addTtmPe`、`addTtmPeQuantile`、`close`、`middleLyrPe`、`middleLyrPeQuantile`、`middleTtmPe`、`middleTtmPeQuantile`、`totalMv`。
- 沪深 300 (`000300.SH`) 返回 5,186 条日数据，范围 `2005-04-08` 至 `2026-08-11`；最后一条实测 `ttmPe=35.16/ttmPeQuantile=0.61994`、`addTtmPe=13.68/addTtmPeQuantile=0.65060`、`middleTtmPe=20.40/middleTtmPeQuantile=0.30505`。自定义中证指数 (`930050.CSI`) 返回 610 条，范围 `2024-01-31` 至 `2026-08-11`，最后一条 `ttmPe=35.28/ttmPeQuantile=0.74262`、`addTtmPe=17.28/addTtmPeQuantile=0.62295`、`middleTtmPe=20.97/middleTtmPeQuantile=0.43443`。
- 该接口本身暴露多个 PE 口径，不能只保存一个无来源的“PE 分位”。实现前必须在版本化规则中锁定指标字段（例如加权滚动 PE 的 `addTtmPeQuantile`，或明确选择等权/中位数），同时保存数据源、指标字段、观测日期和规则版本。

其他候选源的结论：

- 中证指数官网提供更接近官方来源的性能接口：<https://www.csindex.com.cn/csindex-home/perf/index-perf?indexCode=000300&startDate=20200101&endDate=20250630> 返回 1,330 条记录，字段包括 `tradeDate`、指数基本信息、OHLC、成交量、`consNumber` 和 `peg`；2026-08-11 将结束日期延长到当天可返回 1,602 条，最后一条沪深 300 的 `peg` 为 14.44。其前端打包脚本还声明了历史 PE 路径 `/perf/indexCsiDsPe`，实际接口 <https://www.csindex.com.cn/csindex-home/perf/indexCsiDsPe?indexCode=000300&startDate=20200101&endDate=20250630> 返回 1,434 条 `{tradeDate,indexName,indexNameEn,peg}` 记录，最后一条 `peg=12.45`。两个接口同一天的数值与行数不同，不能混用。
- `indexCsiDsPe` 路径名和页面功能语义表明 `peg` 被用于指数 PE 历史，但响应没有解释 `peg` 的计算公式；“PEG”通常也可能表示市盈率相对盈利增长比，因此在项目中只能把它作为“中证可选估值字段”保存并标注来源，不能无证据宣称其算法等价于乐咕的 TTM PE。中证页面和前端脚本未找到公开算法说明或稳定版本合同。
- 中证接口实测无需乐咕式每日 token/cookie，返回 `application/json`，CORS 仅允许中证官网源；这使后端服务端抓取比浏览器直连更可行，但仍需自建超时、缓存、空响应和字段变更保护。其原始 `peg` 序列可在本地按固定规则计算历史分位，避免依赖第三方直接提供的 quantile；窗口、重复交易日处理和最低样本数必须写入规则版本。
- 当前项目的中证、腾讯、同花顺、东方财富行情客户端均只覆盖基金净值/字典/指数 K 线等能力，没有已确认的指数 PE 历史接口。
- 东方财富 Data Center 的尝试接口 `RPT_VALUE_ANALYSIS` 返回 `TRADE_DATE排序列不存在`，`RPT_INDEX_PE` 返回 `报表配置不存在`，不足以形成稳定合同。
- 本机 AkShare 源码核对到指数接口只拉指数 K 线，未发现可直接提供指数 PE 历史序列的稳定 Java/Python 合同。

### 持久化与执行边界建议

- 不能只在执行时计算后丢弃结果：低估正常/高估跳过、均线/涨跌幅数据不足跳过，都需要可查询的执行尝试记录。建议新增独立的计划执行决策/结果记录，以“计划 ID + 北京时间自然日”作为幂等键，记录 `EXECUTED/SKIPPED/FAILED`、基础金额、实际金额、策略版本、数据源/指标、数据日期和跳过原因；现有 `fund_transaction` 唯一索引继续作为交易事实的第二道幂等约束。
- 交易流水仍只保存执行时的实际金额；后续修改计划策略不回写历史交易。Accounting 的 `placePendingForInvestmentPlan` 是现有交易事实边界（`TransactionApi.java:100-107`），策略服务只负责给出决定后的金额并调用该契约。
- 外部 PE/指数数据的请求应在数据库事务外完成，只把经过校验的快照或执行决策放进短事务；这与 [`market-realtime-cache.md`](../../../spec/backend/market-realtime-cache.md:84-100) 的限流、超时、空响应降级和事务边界一致。若第三方接口空响应或会话失败，必须走“数据不可用并跳过”，不能将旧实时估值或固定金额当作替代值。
- 乐咕 API 的页面会话、每日 token、无 SLA 和免责声明使其不适合直接成为“无保护的扣款前置依赖”。若采用，应先做后台定时抓取/本地缓存、超时与限流、来源和口径审计；若不愿承担该外部依赖，应改为管理员导入或本地版本化 PE 快照，缺失时按需求跳过。

## Files found

- `.trellis/tasks/08-11-smart-dca/prd.md` - 已确认的智能定投需求、支付宝规则基线、范围和验收条件。
- `backend/src/main/java/com/fundpilot/backend/investmentplan/domain/investmentplan/InvestmentPlan.java` - 定投聚合与金额/周期校验。
- `backend/src/main/java/com/fundpilot/backend/investmentplan/application/command/planmanagement/InvestmentPlanCommandHandler.java` - 计划创建、更新和 DTO 输入边界。
- `backend/src/main/java/com/fundpilot/backend/investmentplan/application/command/planexecution/InvestmentPlanExecutionCommandHandler.java` - 交易日、计划日、月内和交易幂等门控。
- `backend/src/main/java/com/fundpilot/backend/investmentplan/adapter/scheduler/planexecution/InvestmentPlanExecutionJob.java` - 工作日 14:55 的逐计划调度。
- `backend/src/main/java/com/fundpilot/backend/investmentplan/infrastructure/gateway/planexecution/PlanTransactionGatewayImpl.java` - 定投到 Accounting 的适配与幂等异常转换。
- `backend/src/main/java/com/fundpilot/backend/accounting/adapter/api/transaction/TransactionApi.java` - `INVEST/PENDING` 交易 API 与历史流水事实。
- `backend/src/main/java/com/fundpilot/backend/accounting/adapter/api/position/PositionApi.java` - 持仓成本和确认份额查询。
- `backend/src/main/java/com/fundpilot/backend/productcatalog/adapter/api/product/FundProductApi.java` - 基准指数查询/更新。
- `backend/src/main/java/com/fundpilot/backend/marketdata/adapter/api/publishednav/PublishedNavApi.java` - 基金净值查询。
- `backend/src/main/java/com/fundpilot/backend/marketdata/adapter/api/indexkline/IndexKlineApi.java` - 指数 K 线缓存读写。
- `backend/src/main/java/com/fundpilot/backend/marketdata/application/query/klinequery/KlineQueryHandler.java` - 基准指数 K 线缓存优先与净值降级。
- `backend/src/main/java/com/fundpilot/backend/marketdata/infrastructure/remote/marketfeed/MarketDataSource.java` - 当前行情源能力边界，无 PE 合同。
- `backend/src/main/java/com/fundpilot/backend/investmentplan/application/query/budgetmanagement/InvestmentPlanBudgetSummaryQueryHandler.java` - 当前按固定金额计算预算预测。
- `backend/src/main/resources/db/migration/V42__add_investment_plan.sql` - 新定投与同日唯一索引。
- `backend/src/main/resources/db/migration/V17__nav_accounting_rebuild.sql` - 旧 DCA 同日唯一索引。
- `backend/src/test/java/com/fundpilot/backend/investmentplan/application/command/planexecution/InvestmentPlanExecutionCommandHandlerTest.java` - 正常执行、同日幂等、月内重复和作废基金测试。
- `backend/src/test/java/com/fundpilot/backend/investmentplan/application/command/planmanagement/InvestmentPlanCommandHandlerTest.java` - 金额和频率输入校验测试。
- `frontend/src/pages/DcaPlanFormModal.jsx` - 现有定投表单。
- `frontend/src/pages/DcaManagementPage.jsx` - 现有计划列表和预算入口。
- `frontend/src/api/hooks.js` - 定投/预算 API Hook。
- `frontend/src/dcaBudget.js` - 前端预算进度投影。
- `.trellis/spec/backend/market-realtime-cache.md` - 外部行情限流、缓存、空响应降级和事务边界。
- `.trellis/spec/backend/transaction-consistency.md` - 定投交易日、事务和幂等约束。
- `.trellis/spec/backend/quality-guidelines.md` - Controller/Service/Repository 边界和构造注入约束。

## Code patterns

- 定投 Job 只做批处理遍历和逐计划失败隔离，业务执行在独立的 `@Transactional` Handler（`InvestmentPlanExecutionJob.java:22-31`, `InvestmentPlanExecutionCommandHandler.java:15-41`）。
- 交易幂等由应用层查询/异常和数据库北京自然日唯一索引共同保证（`InvestmentPlanExecutionCommandHandler.java:32-40`, `PlanTransactionGatewayImpl.java:27-37`, `V42__add_investment_plan.sql:75-77`）。
- 跨模块读取通过 API/Gateway 暴露事实，不在定投 Controller 直接查其他模块仓储（`PositionApi.java:206-232`, `PublishedNavApi.java:13-61`, `FundProductApi.java:249-300`）。
- 行情外部请求应走缓存/降级边界，前端读取后端 API，不应直连第三方（`MarketDataSource.java:88-108`, `KlineQueryHandler.java:47-61`, `market-realtime-cache.md:306-327`）。
- 当前测试已经锁定金额传入、重复执行跳过和无效输入错误码，新增策略需在这些测试旁补充固定金额回归、策略结果快照、数据不足跳过和决策记录幂等（`InvestmentPlanExecutionCommandHandlerTest.java:28-106`, `InvestmentPlanCommandHandlerTest.java:27-99`）。

## External references

- 乐咕乐股沪深 300 PE 页面：<https://www.legulegu.com/stockdata/hs300-ttm-lyr>（页面与免责声明，访问/核验日期 2026-08-11）。
- 乐咕乐股自定义指数 PE 页面：<https://www.legulegu.com/stockdata/index-ttm-lyr-pe?indexCode=930050.CSI>（访问/核验日期 2026-08-11）。
- 页面脚本：<https://www.legulegu.com/static/js/lg-charts-new-index-basic-pe.js?date=20260810>；接口路径由脚本解析得到：`/api/stockdata/index-basic-pe?indexCode=<code>&token=<md5-of-date>`。
- 中证指数性能接口：<https://www.csindex.com.cn/csindex-home/perf/index-perf?indexCode=000300&startDate=20200101&endDate=20250630>；中证历史估值接口：<https://www.csindex.com.cn/csindex-home/perf/indexCsiDsPe?indexCode=000300&startDate=20200101&endDate=20250630>（响应字段 `peg`，访问/核验日期 2026-08-11）。
- 支付宝策略规则不使用运行时接口；采用本任务 PRD 记录的 2025-06 App 截图核验快照（`prd.md:18`, `prd.md:57`）。

## Related specs

- `.trellis/spec/backend/market-realtime-cache.md:84-100`：外部调用预算、降级链、空响应语义及“外部请求在事务外”。
- `.trellis/spec/backend/transaction-consistency.md:147-155`：账本重建、定投交易幂等和逐基金独立事务。
- `.trellis/spec/backend/quality-guidelines.md:10-14`：构造注入与 Controller 只路由规则。
- `.trellis/tasks/08-11-smart-dca/prd.md:15-44`：本任务的策略、预算、历史事实和验收要求。

## Caveats / Not Found

- `python ./.trellis/scripts/task.py current --source` 在本次研究上下文返回 `Current task: (none)`；研究文件按委派明确指定的 `.trellis/tasks/08-11-smart-dca/` 目录保存，未修改任务状态或其他目录。
- 未找到官方公开的支付宝智能定投策略 API；也未找到现有项目行情客户端中的指数 PE 历史接口。支付宝规则和策略阈值只能按本地版本化快照维护。
- 乐咕数据可读性依赖先访问页面建立 cookie 会话；不带会话的 API 实测 HTTP 200 且空体。页面和接口未发现稳定 SLA、版本兼容承诺或明确的自动化使用许可，不能把当前路径视为长期合同。
- 乐咕响应同时提供等权、加权和中位数的静态/TTM PE 分位；“指数 PE”具体采用哪个字段仍需在设计阶段锁定，否则不同字段会产生不同的低估判断。`dataStartDate` 也会因指数不同而不同，不能假设所有指数都有完整历史。
- 中证官方响应的 `peg` 与乐咕 `addTtmPe/ttmPe` 不是同一口径：2026-08-11 沪深 300 分别实测为 `14.44` 与 `13.68/35.16`。若选中证源，应固定使用中证字段并在本地计算分位，不能跨源拼接历史序列。
- `930050.CSI` 的实测历史从 2024-01-31 开始，历史较短；当数据不足以支撑规则时必须按 PRD 跳过并记录原因。
- 研究未修改业务代码、数据库迁移、测试或前端文件；当前工作区的其他 Trellis/用户改动保持原样。
