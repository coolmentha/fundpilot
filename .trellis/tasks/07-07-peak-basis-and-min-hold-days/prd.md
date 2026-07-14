# 卖出纪律峰值基准切换与最小持有期可配

## Goal

让卖出纪律（移动止盈）从"单一持有期高点基准 + 写死 5 交易日冷启动"演进为"峰值基准可切换 + 冷启动窗口可配"，
使同一套止盈档位在不同节奏需求下产生差异化卖出时机。本任务只做卖出侧，买入仍由定投负责（ADR-0016）。

## Background

- 当前移动止盈只用 `holdingPeriodPeakNav`（建仓后最高累计净值）一个基准（`DisciplineStrategyService.checkTrailingStop`）。
- `MIN_HOLD_DAYS = 5` 是 `DisciplineStrategyService:50` 的常量，所有基金共用。
- 峰值已实时派生（ADR-0001）：`FundNavHistoryRepository.findPeakAccumulatedNav` / `findPeakAccumulatedNavSince(since)`，
  `since` 过滤能力已具备，滚动窗口只需传 `today - N 天`。
- `tradingDaysSinceLastBuy` 由 `SignalGenerationService:157` 用 `tradingCalendarService.daysBetweenTradingDays` 计算，链路完整。
- 当前移动止盈为单值 `stopLossPullbackPercent`（回落 n×阈值卖 holdingShares×(n/4)）；
  本任务在其上新增基准切换与冷启动窗口两个独立维度，不改动 `stopLossPullbackPercent` 语义。

## Confirmed Scope

- 新增移动止盈峰值基准枚举 `PeakNavBasis`：HOLDING_PERIOD / ROLLING_60D / ALL_TIME（三档固定，窗口不可自由填）。
- `FundStrategyEntity` 增加 `peakNavBasis` 字段；存量策略字段为 null 时 fallback 到 HOLDING_PERIOD 行为。
- `MIN_HOLD_DAYS` 从 `DisciplineStrategyService` 常量改为 `FundStrategyEntity.minHoldDays` 字段，默认 5、区间 [1, 20]。
- `SignalGenerationService.buildCapitalContext` 按基准模式派生不同的 peak 注入 `CapitalContext`（移动止盈专用）；
  逻辑止损的回撤基准（peakNav）不受影响。
- 更新 `CONTEXT.md` 的「持有期高点」「7 天内不赎回硬约束」两节契约；必要时补 ADR。
- 同步 entity / request DTO / view DTO / frontend 表单与展示 / 测试。

## Out Of Scope

- 逻辑止损参数化（年线周期、放量倍数等）——单独任务。
- 估值止盈（PE/PB 分位）——单独任务，待估值数据源确认。
- 止损侧分批化、硬止损线。
- 波动率自适应、参数回测预览。
- 买入/加仓信号（定投负责，见 ADR-0016）。
- 将基准模式做成预设包（保持 `stopLossPullbackPercent` 单值 + 两个新字段独立维度）。

## Requirements

- R1: 用户可为每个策略在三档固定基准中选择移动止盈的峰值基准：HOLDING_PERIOD（持有期高点，现有）、ROLLING_60D（滚动 60
  日高点）、ALL_TIME（全历史前高）。
- R2: 基准模式由后端解析，前端只传枚举值；滚动窗口长度固定 60 日，不接受前端自由填写。
- R3: MIN_HOLD_DAYS 可按策略配置，默认 5 交易日，允许区间 [1, 20]；越界拒绝并返回错误码。
- R4: 存量策略（`peakNavBasis` / `minHoldDays` 为 null）必须产生与当前完全一致的信号结果（基准回落到 HOLDING_PERIOD、窗口回落到
  5）。
- R5: 基准切换只影响移动止盈；逻辑止损的 peakNav 回撤基准（全历史前高）保持不变。
- R6: 逻辑止损豁免 MIN_HOLD_DAYS 的语义保持（自定义窗口下仍豁免，但记 MIN_HOLD_DAYS_OVERRIDDEN）。
- R7: 策略列表与详情需展示基准模式与冷启动窗口。
- R8: 保持与现有 `stopLossPullbackPercent` / 四档 trailing API payload 的兼容（新增字段可选）。

## Acceptance Criteria

- [ ] 选择 HOLDING_PERIOD 时，信号结果与当前实现逐位一致（baseline 回归测试）。
- [ ] 选择 ROLLING_60D 时，回落计算基于 `max(nav) WHERE nav_date >= today - 60 天`。
- [ ] 选择 ALL_TIME 时，回落计算基于 `findPeakAccumulatedNav`（无 since 过滤）。
- [ ] MIN_HOLD_DAYS 自定义值在 [1, 20] 生效；默认/null 时等价于 5；越界返回校验错误。
- [ ] 存量策略（新字段为 null）fallback 到现有逻辑，信号逐位一致。
- [ ] 逻辑止损在任何基准/窗口下均豁免 MIN_HOLD_DAYS，warnings 仍记 MIN_HOLD_DAYS_OVERRIDDEN。
- [ ] CONTEXT.md「持有期高点」「MIN_HOLD_DAYS」两节契约更新；如行为演进超出原文范围，补一条 ADR。
- [ ] 后端编译通过，`DisciplineStrategyServiceTest` 全绿并新增覆盖三档基准与可配窗口的分支用例。
- [ ] 前端构建通过，策略表单与展示包含两个新字段。

## Technical Notes

- 峰值派生复用 `FundNavHistoryRepository`：
    - HOLDING_PERIOD → `findPeakAccumulatedNavSince(fund.openedAt)`（现有）
    - ROLLING_60D → `findPeakAccumulatedNavSince(today.minus(60, DAYS))`（复用现有方法，仅换入参）
    - ALL_TIME → `findPeakAccumulatedNav`（现有，零改动）
- `MIN_HOLD_DAYS` 常量改为读取 `strategy.getMinHoldDays()`，null 时回退 5；`applyMinHoldDays` 逻辑不变。
- `CapitalContext` 现有 `peakNav`（逻辑止损用）与 `holdingPeriodPeakNav`（移动止盈用）两字段语义不变；
  `SignalGenerationService.buildCapitalContext` 按基准模式决定注入 `holdingPeriodPeakNav` 的值。
- DB 迁移：`fund_strategy` 增 `peak_nav_basis VARCHAR(32)` 与 `min_hold_days INTEGER`，均 nullable 兼容存量。

## Risks

- DB 迁移触及 `fund_strategy`；回滚需删除两个新列。
- 基准模式跨层字段需对齐 entity / request / view / frontend form / display，任一层遗漏会导致 fallback 误触发。
- ROLLING_60D 在基金净值历史不足 60 日时，`findPeakAccumulatedNavSince` 的 `since` 早于最早净值——
  需明确降级策略（建议：取最早可得净值日为下限，等价于全历史），在 R2 之外的边界用例需测试覆盖。

## Open Questions

- None blocking for this confirmed version.
