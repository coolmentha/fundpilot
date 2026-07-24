# Design: 峰值基准切换与最小持有期可配

## 架构与边界

本任务跨 4 层 + DB 迁移 + 契约文档，但改动面集中、无新抽象：

```
┌─ frontend ─────────────────────────────────────────────┐
│ StrategyFormModal.jsx + FundStrategyTab.jsx            │
│   新增: peakNavBasis 下拉 / minHoldDays 数字输入       │
│   constants.js: PEAK_NAV_BASIS_OPTIONS                  │
└────────────────────────────────────────────────────────┘
┌─ backend controller ───────────────────────────────────┐
│ FundStrategyView (record)  +2 字段                     │
│ StrategyConfigRequest (record) +2 字段                 │
└────────────────────────────────────────────────────────┘
┌─ backend service ──────────────────────────────────────┐
│ StrategyConfigService     校验 minHoldDays ∈ [1,20]    │
│ SignalGenerationService   按 basis 派生 peak 注入      │
│ DisciplineStrategyService MIN_HOLD_DAYS 改读 strategy  │
└────────────────────────────────────────────────────────┘
┌─ backend entity / enums ───────────────────────────────┐
│ FundStrategyEntity  +peakNavBasis +minHoldDays          │
│ PeakNavBasis (新 enum, implements EnumValue)           │
└────────────────────────────────────────────────────────┘
┌─ DB ───────────────────────────────────────────────────┐
│ V15__add_peak_nav_basis_and_min_hold_days.sql          │
└────────────────────────────────────────────────────────┘
```

## 数据流：信号生成时的基准派生

`SignalGenerationService.buildCapitalContext` 是唯一改动点。

```
当前:
  peakNav          = findPeakAccumulatedNav(fundId)           // 逻辑止损用,不变
  holdingPeakNav   = findPeakAccumulatedNavSince(openedAt)    // 移动止盈用,固定
  -> new CapitalContext(peakNav, holdingPeakNav, ...)

改后(按 strategy.peakNavBasis 分派):
  peakNav          = findPeakAccumulatedNav(fundId)           // 不变
  trailingPeakNav  = switch(strategy.peakNavBasis):
                       HOLDING_PERIOD -> findPeakAccumulatedNavSince(openedAt)
                       ROLLING_60D   -> findPeakAccumulatedNavSince(today.minus(60,DAYS))
                       ALL_TIME      -> findPeakAccumulatedNav(fundId)
                       null          -> findPeakAccumulatedNavSince(openedAt)  // fallback
  -> new CapitalContext(peakNav, trailingPeakNav, ...)
```

**CapitalContext record 签名不变**——第二参数语义从"持有期高点"放宽为"移动止盈基准高点"， 仅更新 javadoc。这样
`DisciplineStrategyService.checkTrailingStop` 零改动，仍读 `capital.holdingPeriodPeakNav()`。

## MIN_HOLD_DAYS 改造

`DisciplineStrategyService`:

- 删 `private static final int MIN_HOLD_DAYS = 5;`
- `applyMinHoldDays` 增参 `int minHoldDays`，由 `evaluateSignal` 从 `strategy.getMinHoldDays()` 读取（null→5）后传入。
- 逻辑止损豁免分支与 warning 语义不变。

## 校验规则（StrategyConfigService）

- `peakNavBasis`: 非 null 时必须是合法枚举值。
    - 建议写入时强制非 null，默认 HOLDING_PERIOD，避免 fallback 路径长期存在。
- `minHoldDays`: null→存 null（fallback 到 5）；非 null 时必须 ∈ [1, 20] 整数，否则抛 `STRATEGY_CONFIG_INVALID`
  （ErrorCode.java:21 已存在）。

## 兼容与迁移

### DB 迁移 V15

```sql
ALTER TABLE fund_strategy
    ADD COLUMN peak_nav_basis VARCHAR(32),
    ADD COLUMN min_hold_days INTEGER;

-- 存量策略回填默认值,消除长期 fallback 路径
UPDATE fund_strategy
SET peak_nav_basis = 'HOLDING_PERIOD',
    min_hold_days = 5
WHERE peak_nav_basis IS NULL;
```

**关键决策：回填而非留 NULL**。两个字段语义简单、无版本协商需求，回填让代码路径单一化 （service 不必长期维护 null
分支）。存量行为逐位一致：HOLDING_PERIOD = 现有 holdingPeriodPeakNav，5 = 现有常量。

注：现有最大迁移版本为 V9（V10–V14 跳号），本任务用 V15。

### API 兼容

- `StrategyConfigRequest` 新增 2 字段可选；旧客户端不传时，后端按 HOLDING_PERIOD / 5 处理。
- `FundStrategyView` 新增 2 字段；旧前端忽略多余字段不受影响。

## 降级：ROLLING_60D 净值历史不足

`findPeakAccumulatedNavSince(today.minus(60,DAYS))` 当基金净值历史不足 60 日时， SQL 仍返回窗口内
max（即所有可得净值的高点），等价于"全历史前高"——无需额外代码， 在测试用例覆盖此边界即可。

## 契约更新点

### CONTEXT.md

- 「持有期高点 holdingPeriodPeakNav」节：扩展为"移动止盈基准高点"，说明三档基准。
- 「7 天内不赎回硬约束 MIN_HOLD_DAYS」节：5 改为"默认 5，可配 [1,20]"。

### ADR

- 行为演进在原文范围内（仍是持有期高点派生 + MIN_HOLD_DAYS 窗口判定），不必新增 ADR。
- 若 review 时认为"基准可切换"超出 ADR-0001 的派生语义边界，再补一条 ADR 记录决策。

## 重要权衡

| 决策                | 选择           | 否决项                | 理由                                    |
|---------------------|----------------|-----------------------|-----------------------------------------|
| 基准选项            | 三档固定       | 两档/类型+窗口可配    | 覆盖三类节奏且复用现有 repo 方法        |
| 窗口长度            | 固定 60 日     | 可自由填写            | 减少校验复杂度，60 日是趋势判断常用窗口 |
| MIN_HOLD_DAYS 区间  | [1,20]         | [3,10]/[1,30]         | 覆盖长持场景又不过干泛                  |
| 字段 null 处理      | 迁移回填默认值 | 保留 null 走 fallback | 单一代码路径，避免长期维护 null 分支    |
| CapitalContext 签名 | 不变           | 加 basis 字段         | service 层零改动，改动面最小            |

## 回滚

- DB：`ALTER TABLE fund_strategy DROP COLUMN peak_nav_basis, DROP COLUMN min_hold_days;`
- 代码：revert commit 即可，无外部状态依赖。
- 信号历史：本任务不改 SignalLog 结构，历史信号不受影响。
