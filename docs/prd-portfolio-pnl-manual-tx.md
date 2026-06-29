## Problem Statement

作为 FundPilot 的个人投资者用户，我在日常使用中发现四个体验断层：

1. **交易与基金割裂**：交易管理是一个独立页面，但里面只有一个"输入交易ID撤单"的占位功能，看不到任何交易流水。想看某只基金的买卖记录，得自己记住交易ID再撤单——这个页面毫无价值。流水本就该在对应基金里查看。
2. **操作只能走信号**：当前所有交易必须先生成信号→在确认页确认→才生成交易。但我跨平台迁入已有持仓、或想手动调整仓位时，没有信号可确认，就无法录入交易。领域模型其实已预留手动交易（`signalLog=null`），只是前端没入口。
3. **计划仓位可以乱填**：`plannedTotalAmount` 是该基金目标投入总额，是金字塔加仓的分母。但新建基金时随便填多少都行，如果我填了 100 万但总可投资金才 50 万，建仓信号会永远被硬约束卡住——填了一个根本建不了的死状态。
4. **看不到钱的变化**：基金列表和概览页只展示静态信息（代码/名称/类型/计划仓位），看不到今日涨跌、现有仓位市值、今日盈亏。概览页的 KPI 只有"待确认操作数/持仓基金数/总可投资金/计划仓位占比"，没有盈亏视角——这是我每天打开 App 最想看的东西。

## Solution

从用户视角，做四件事：

1. **合并交易管理到基金**：删除独立的"交易管理"页，交易流水作为 Tab 放进基金详情页，撤单操作在流水行内完成。
2. **支持手动录入交易**：在基金详情页的"交易流水" Tab 提供"手动录入"入口，支持加仓/减仓/转入/转出/定投五类操作，不经过信号。复用现有 `FundTransactionEntity`（`signalLog=null`），与信号触发交易共用同一套账目和持仓聚合。
3. **校验计划仓位**：新建/编辑基金时校验 `plannedTotalAmount ≤ 总可投资金 × 单品种仓位上限`，超限报错不让填。
4. **展示盈亏与涨跌**：基金列表和详情页展示今日涨跌、持仓市值、今日盈亏、总盈亏；概览页 KPI 增加今日盈亏合计、上涨/下跌基金数、盈利/亏损基金数。所有基金都拉净值算涨跌（不限 EFFECTIVE 策略）。

## User Stories

1. 作为投资者，我想在基金详情页看到该基金的全部交易流水，这样我能回顾买卖记录而不用去单独的交易管理页。
2. 作为投资者，我想在交易流水列表里直接撤单 PENDING 状态的交易，这样我能快速取消误操作的下单。
3. 作为投资者，我想在基金详情页手动录入一笔加仓交易，这样我能把别的平台已买入的持仓迁入 FundPilot。
4. 作为投资者，我想在基金详情页手动录入一笔减仓交易，这样我能在没有卖出信号时自行减仓。
5. 作为投资者，我想在基金详情页手动录入一笔转入交易，这样我能记录从其他基金转换过来的份额。
6. 作为投资者，我想在基金详情页手动录入一笔转出交易，这样我能记录转换到其他基金的份额。
7. 作为投资者，我想在基金详情页手动录入一笔定投交易，这样我能记录每期定投扣款（每期落一条）。
8. 作为投资者，我想手动买入时填金额（份额等净值确认后回填），这样符合场外基金"先下单后知份额"的现实。
9. 作为投资者，我想手动卖出时填份额（金额等净值确认后回填），这样符合场外基金"先下单后知金额"的现实。
10. 作为投资者，我想手动交易不走7天硬约束卡顿，这样我能灵活操作（前端可提示但不阻止）。
11. 作为投资者，我想新建基金时如果计划仓位超过单品种上限就报错，这样我不会填一个根本建不了的死状态。
12. 作为投资者，我想编辑基金时如果计划仓位改到超过单品种上限也报错，这样我能及时调整。
13. 作为投资者，我想在基金列表看到每只基金的今日涨跌幅，这样我能快速扫一眼哪些涨哪些跌。
14. 作为投资者，我想在基金列表看到每只基金的持仓市值，这样我知道实际投了多少钱。
15. 作为投资者，我想在基金列表看到每只基金的今日盈亏，这样我知道今天净值变动让我多了/少了多少钱。
16. 作为投资者，我想在基金列表看到每只基金的总盈亏，这样我知道整体是赚还是亏。
17. 作为投资者，我想在基金详情页看到今日涨跌、持仓市值、今日盈亏、总盈亏，这样我在单只基金视角也能看到钱的变化。
18. 作为投资者，我想在概览页看到今日盈亏合计，这样我一打开 App 就知道今天整体赚了还是亏了。
19. 作为投资者，我想在概览页看到今天上涨的基金数和下跌的基金数，这样我能感知今天整体市场情绪。
20. 作为投资者，我想在概览页看到盈利的基金数和亏损的基金数，这样我知道持仓里哪些整体在赚、哪些在亏。
21. 作为投资者，我想未建仓的基金也能看到今日涨跌，这样我观察池里的基金也有行情参考。
22. 作为投资者，我想盈亏数字用累计净值算（不是单位净值），这样分红除权不会让跌幅"虚高"。
23. 作为投资者，我想今日盈亏反映"今天净值变动带来的账面盈亏"，这样它和"总盈亏"语义不混淆。
24. 作为投资者，我想"上涨/下跌"和"盈利/亏损"是两个独立维度，这样今日上涨但整体亏损的基金能被正确归类。

## Implementation Decisions

### 计划仓位校验
- 在 `FundService.create` 和 `FundService.update` 中新增校验：`plannedTotalAmount ≤ UserConfig.totalInvestableCapital × HardConstraintConfig.singlePositionLimit(fundCategory)`。
- 超限抛 `BusinessException`，新增 `ErrorCode.PLANNED_AMOUNT_EXCEEDS_LIMIT`。
- 校验依赖 `UserConfigRepository`（已有），若 `totalInvestableCapital` 未配置则先要求配置（复用现有 `USER_CONFIG_NOT_INITIALIZED`）。
- `plannedTotalAmount` 为 null 时不校验（更新时允许不传）。

### 盈亏与涨跌计算（FundPnlService）
- 新增 `FundPnlService`，提供单基金和组合两个层面的计算。
- **今日涨跌幅**：从 `fund_nav_history` 取最近两期累计净值（`accumulatedNav`），`涨跌幅 = (最近 - 上一期) / 上一期`。
- **今日盈亏**：`持仓份额 × (最近累计净值 - 上一期累计净值)`，持仓份额取 `FundPositionService.getHoldingShares`（CONFIRMED 净聚合）。
- **总盈亏**：`当前市值 - 持仓成本`。当前市值 = `持仓份额 × 最近累计净值`；持仓成本 = Σ CONFIRMED 交易的 amount（INCREASE/TRANSFER_IN/INVEST 的 amount 之和，减去 DECREASE/TRANSFER_OUT 的 amount 之和）。
- **组合汇总**：遍历所有 HOLDING 基金，聚合今日盈亏合计、统计上涨/下跌基金数（按今日涨跌幅）、盈利/亏损基金数（按总盈亏正负）。
- 纯算术部分抽成 `support/FundPnlCalculator` 纯函数（无 Spring 依赖），便于单测构造数值覆盖各分支。

### 行情拉取范围扩大
- `MarketDataFetchService.fetchBatch` 的基金遍历范围从 `fundStrategyRepository.findEffectiveFundIds()` 改为查**所有未软删基金**（`fundRepository.findAll()`）。
- 净值历史落库逻辑保持不变（已有 `pingzhongdata.js` 拉取）。未建仓基金也会被拉取，使其有 `fund_nav_history` 数据支撑今日涨跌。
- 分批策略不变（`fundId.hashCode() % 3`），但基金总数增加，需确认拉取时长可接受（基金数量级有限，分钟内可完成）。

### 手动交易端点
- 新增 `POST /api/funds/{fundId}/transactions`：手动录入交易。请求体含 `source`（FundTransactionSource 五值）、`amount`（买入时填）、`shares`（卖出时填）。`signalLog=null`、`status=PENDING`，走 NavConfirmJob 回填另一侧。
- 新增 `GET /api/funds/{fundId}/transactions`：查某基金交易流水列表，按 `createdDate` 倒序，分页。
- 复用 `FundTransactionEntity`，不新建表。买入写 amount/shares=null，卖出写 shares/amount=null，与信号触发交易一致。
- 手动卖出不经过 `evaluateSignal`，不卡 7 天硬约束（CONTEXT.md 已明确，前端可提示但不阻止）。
- 手动交易同样受 NavConfirmJob 回填（当晚净值确认后转 CONFIRMED）。

### 基金 View 扩展
- `FundView` 增字段：`dailyChangePct`（今日涨跌幅，BigDecimal，可空）、`holdingShares`（持仓份额）、`holdingAmount`（持仓市值）、`dailyPnl`（今日盈亏）、`totalPnl`（总盈亏）。无持仓或无净值数据时对应字段为 null。
- 新增 `PortfolioSummaryView`：`dailyPnlTotal`（今日盈亏合计）、`risingFundCount`（上涨基金数）、`fallingFundCount`（下跌基金数）、`profitableFundCount`（盈利基金数）、`losingFundCount`（亏损基金数）。
- 新增 `GET /api/portfolio/summary` 端点返回 `PortfolioSummaryView`。

### 前端改造
- 删除 `/transactions` 路由和导航菜单项，删除 `TransactionsPage.jsx`。
- 基金详情页 `FundDetailPage` 的 Tabs 新增"交易流水"项（`FundTransactionTab`）：交易列表表格（日期/来源/金额/份额/净值/状态）+ PENDING 行内撤单按钮 + "手动录入"按钮（弹窗选来源、填金额或份额）。
- 基金列表 `FundsPage` 列增加：今日涨跌（涨红跌绿）、持仓市值、今日盈亏、总盈亏。
- 概览页 `DashboardPage` KPI 改为：今日盈亏合计、上涨基金数、下跌基金数、盈利基金数、亏损基金数（替换部分现有 KPI 或新增一行）。

## Testing Decisions

### 测试原则
- 只测外部行为，不测实现细节。
- 优先用最高 seam：纯算术抽纯函数单测，涉及多表聚合用 Service 集成测试。
- 不写 MockMvc 端到端测试（与 issue #16 一致），Controller 层只做冒烟验证 bean 注入。

### 测试模块
- **`FundPnlCalculatorTest`（纯函数单测，新）**：今日涨跌幅、今日盈亏、总盈亏的纯算术。构造数值覆盖：正常涨跌、净值为零、无持仓、分红除权场景。先例：`MaxDrawdownCalculatorTest`、`CoefficientTableTest`。
- **`FundPnlServiceTest`（集成测试，新）**：多表聚合——落 fund_nav_history + CONFIRMED 交易，验证今日盈亏/总盈亏/组合汇总。先例：`FundPositionServiceTest`（持仓份额聚合，同样模式）。
- **`FundServiceTest`（集成测试，新）**：计划仓位校验——超限抛异常、不超限正常创建。先例：`FundArchiveServiceTest`。
- **手动交易测试**：归入 `FundPnlServiceTest` 或新建 `ManualTransactionServiceTest`——验证 signalLog=null、买入写 amount/卖出写 shares、NavConfirmJob 回填。先例：`NavConfirmAndCancelServiceTest`。
- **ControllerSmokeTest 扩展**：新增 `FundTransactionController`、`PortfolioController` 的 bean 注入验证。先例：现有 `ControllerSmokeTest`。

## Out of Scope

- **定投计划**：本期手动定投是"每期录一条单次交易"，不建定投计划（DcaPlanEntity）、不做自动扣款。CONTEXT.md 明确"不做定投的话另起 DcaPlanEntity 表"。
- **基金转换的互指关系**：手动转入/转出作为独立交易录入，不在本期自动建立 `relatedTransaction` 互指（用户分两笔录）。自动互指留待将来。
- **盘中实时净值**：场外基金无盘中实时净值，"今日涨跌"是最近一期净值变动（T-1 vs T-2），不做实时刷新。
- **Redis 缓存**：盈亏计算实时聚合，不引入缓存（CONTEXT.md 已明确本期不引入 Redis）。
- **多用户**：本期单用户，不预留 userId。

## Further Notes

- **两个正交维度**：上涨/下跌（今日净值变动率）与盈利/亏损（总盈亏正负）是两个维度，一只基金可能今日上涨但整体亏损。前端展示时两者独立计数，不混用。
- **今日盈亏 vs 总盈亏**：今日盈亏 = 持仓份额 × (最近净值 - 上一期净值)，反映单日净值变动；总盈亏 = 当前市值 - 持仓成本，反映累计盈亏。两者口径不同，概览页同时展示。
- **计划仓位校验与硬约束互补**：计划仓位校验管"意图上限"（建仓时防死状态），硬约束管"事实上限"（信号生成时卡实际持仓占比）。两者不替代，信号生成时的硬约束检查保持不变。
- 领域术语已固化在 `CONTEXT.md`：`计划仓位校验`、`今日涨跌/今日盈亏/总盈亏`、`上涨下跌vs盈亏基金`、`手动交易`。
