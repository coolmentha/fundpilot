# 移除金字塔加仓机制,移动止盈解耦为独立卖出纪律

## Goal

平台已转向"实时行情工作台,定投止盈为辅"的定位。金字塔择时加仓机制(建仓/四档加仓/计划总仓位)不再符合产品方向。本次移除金字塔加仓的全部机制,将卖出侧的移动止盈从金字塔档位状态中解耦为独立的"按回落分档减仓"规则,逻辑止损保留。买入建议(BUILD/ADD)信号随之退场——买入完全由用户手动/定投决定,平台只提供卖出纪律辅助。

## Background

- `plannedTotalAmount`(fund 表)是金字塔加仓的金额分母:建仓额=`plannedTotalAmount×0.10`,加仓额=`plannedTotalAmount×tierRatio`。
- `fund_strategy` 表 14 个业务列里 13 列(tier1~4_drawdown/ratio/added_at 共 12 列 + weekly_cool_down_threshold)纯金字塔专属,1 列(stop_loss_pullback_percent)是止盈参数但实现深度耦合金字塔档位。
- 当前移动止盈 `checkTrailingStop` 依赖 `tier1~4AddedAt`(找可卖档)+ `tierAddShares`/`buildShares`(按档份额卖出),无法独立存活。
- 代码里无独立"定投策略"实体(无定投金额/频率/周期配置);"定投"在代码里只是 ① 交易来源标签 INVEST ② 回测对照基准。本次不新建定投配置,属未来功能。
- 总可投资金及连带的总仓位≤80%硬约束、计划仓位校验、再平衡减仓已在上个会话(#67 / V9)移除。
- `singlePositionPct`/`categoryPositionPct`/`totalEquityAmount` 仅被 `applyHardConstraints`(BUILD/ADD 硬约束)消费,随 BUILD/ADD 删除一并退场。
- `weeklyCoolDownThreshold` 消费者:逻辑止损 ACTIVE 分支、加仓 warnings、回测/寻优、StrategyConfigRequest/View——全部随金字塔删除。

## Requirements

### R1 删除金字塔加仓机制
- 删 `FundEntity.plannedTotalAmount` 字段及 fund 表 `planned_total_amount` 列(V10 迁移)。
- 删 `FundStrategyEntity` 的 tier1~4_drawdown/ratio/added_at(12 列)+ weekly_cool_down_threshold;保留 stop_loss_pullback_percent。
- 删 BUILD 信号(`decideBuild`)、ADD 信号(`decideAdd`)及其附属逻辑(反弹清空 `clearTiersOnRebound`、加仓 warnings `addWarnings`、BUILD/ADD 硬约束 `applyHardConstraints`)。
- 删 `CapitalContext` 的 `plannedTotalAmount`、`buildShares`、`tierAddShares`、`singlePositionPct`、`categoryPositionPct`、`totalEquityAmount` 六字段(10→4 字段:peakNav, holdingPeriodPeakNav, holdingShares, lastBuyConfirmTime)。
- 删 `HardConstraintChecker.check4` / `HardConstraintConfig`(singlePositionLimit/categoryPositionLimit/BUILD_RATIO 等)——BUILD/ADD 没了,硬约束无人调用。
- `SignalGenerationService.buildCapitalContext` / `computeLastBuyConfirmTime` 同步清理(后者改为只取最近 CONFIRMED 交易 confirmTime,不再读 tier1~4AddedAt);删 `buildTierAddShares`、`computeTotalEquityAmount`、`computeCategoryPositionPct`。
- 前端删 plannedTotalAmount 表单项(必填)、列表列、详情展示;删策略表单 tier1~4 编辑项。

### R2 移动止盈解耦为独立"按回落分档减仓"
- 重写 `checkTrailingStop`:回落 `n×stopLossPullbackPercent`(n=4→1 取最大 n)触发,卖出份额 = `holdingShares × (n/4)`。
  - 回落 1×阈值 → 卖 1/4;2×阈值 → 卖 1/2;3×阈值 → 卖 3/4;4×阈值 → 全卖。
- 不再读 `tier1~4AddedAt` / `tierAddShares` / `buildShares`。
- `stop_loss_pullback_percent` 字段保留(唯一止盈阈值参数),策略表单保留其编辑入口。

### R3 保留逻辑止损与 7 天持有期
- `checkLogicBrokenStopLoss`:ETF/INDEX 分支不变(跟踪指数放量下跌);ACTIVE 分支移除 `weeklyCoolDownThreshold` 条件(字段已删),ACTIVE 逻辑止损改为只看"破年线 + MACD绿柱扩大"两条件。
- 7 天持有期 MIN_HOLD_DAYS:完整保留(与金字塔无关),逻辑止损豁免、移动止盈不豁免。

### R4 回测/寻优退场
- 删除 `DefaultStrategyBacktestService`/`BacktestSimulator`/`BenchmarkCalculator`/`BacktestParams`/`StrategyOptimizeService`/`OptimizeParamRanker`/`OptimizeGridGenerator`/`OptimizeParams`/`DefaultTierTable`/`DefaultCoolDownTable`/`TierDefaults` 整套金字塔寻优配套。
- 删 `StrategyConfigService.calibrate` 及 calibrate 相关状态流转(PENDING_CALIBRATION/CALIBRATION_FAILED/CALIBRATED 枚举值保留供存量数据,但不再产生新流转)。
- 前端策略页"校准"按钮、回测对照列移除。

### R5 文档与 ADR
- CONTEXT.md:彻底清理金字塔加仓、计划仓位校验、再平衡、BUILD/ADD 信号、硬约束相关章节(部分已标~~已移除~~)。
- 新增 ADR 记录"金字塔退场,移动止盈解耦为独立卖出纪律"。
- SignalReason 枚举:BUILD/ADD/REBALANCE/NO_ADD_TIER/NO_TIER_TO_SELL/BUILD_CONDITION_NOT_MET 等值保留(@Deprecated,供存量 SignalLog 反序列化),前端 labels 保留供历史信号展示。

## Acceptance Criteria

- [ ] AC1 `fund` 表无 `planned_total_amount` 列;`fund_strategy` 表无 tier1~4_* 及 weekly_cool_down_threshold 列,仅留 stop_loss_pullback_percent(V10 迁移生效)。
- [ ] AC2 信号引擎不再产出 BUILD/ADD 信号;`evaluateSignal` 对 PENDING_HOLDING 返 NONE,对 HOLDING 只判 SELL(逻辑止损/移动止盈)或 NONE。
- [ ] AC3 移动止盈:回落 ≥1×阈值卖 holdingShares×1/4,≥2×卖 1/2,≥3×卖 3/4,≥4×全卖;不依赖任何 tier 字段。
- [ ] AC4 逻辑止损 ETF 分支不变;ACTIVE 分支不再读 weeklyCoolDownThreshold,只看破年线+MACD绿柱扩大。
- [ ] AC5 7 天持有期:移动止盈未满 5 交易日降级 NONE,逻辑止损豁免。
- [ ] AC6 前端基金表单无"计划总仓位"项;策略表单无 tier 编辑项,仅留"移动止盈回落";无"校准"按钮。
- [ ] AC7 回测/寻优代码路径删除,calibrate 接口移除,前端无触发入口。
- [ ] AC8 `mvn test` 全绿(CI Testcontainers);`npm run build` 通过。
- [ ] AC9 CONTEXT.md 无金字塔加仓/计划仓位校验/硬约束残留描述;新增 ADR。
- [ ] AC10 存量 SignalLog 历史数据不受影响(只删 fund_strategy 运行时列,不删 signal_log 表)。

## Out of Scope

- 新建定投配置实体(定投金额/频率/周期)——属未来功能。
- K 线/行情工作台改动——本次不动行情侧。
- signal_log 表结构变更——历史信号保留。

## Open Questions

- 无(需求已通过 AskUserQuestion 两轮澄清确认)。
