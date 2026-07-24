# Design: 定投止盈类型推荐

## 架构与边界

本需求保持现有“信号是建议、交易由用户确认”的边界。`FundStrategyEntity` 继续同时承担策略版本参数与该版本运行时状态；不新增独立策略配置表。新增职责拆为三部分：

1. `TakeProfitPresetService`：四类基金推荐值的唯一事实源，负责推荐查询、默认填充和自定义判定。
2. `TakeProfitLifecycleService`：定投止盈周期状态机、成熟 lot 计算、卖出份额上限和交易确认/撤销后的状态推进。
3. `DisciplineStrategyService`：继续保留纯计算核心，逻辑止损优先；仅在生命周期服务判定为可评估时计算移动止盈。

`FundCategory` 决定推荐模板；`FundSubType` 继续只决定行情数据和逻辑止损分支。

## 数据模型

### FundStrategyEntity 配置字段

- `profitActivationPercent`：整仓收益率达到该值后启动止盈。
- `stopLossPullbackPercent`：保留存量字段/API 名以避免破坏兼容，语义统一为正数“周期高点回撤比例”。
- `profitHarvestPercent`：本次收割浮盈比例。
- `minimumHoldingPercent`：止盈后最低保留仓位。
- `maxSingleSellPercent`：单次最多卖出当前持仓比例。
- `cooldownTradingDays`：止盈后的交易日冷静期。
- `presetFundCategory`：创建/最后恢复推荐时使用的基金类型。
- `presetVersion`：推荐模板版本，首版 `1`。
- `customized`：当前参数是否偏离记录的推荐模板。

### FundStrategyEntity 运行时字段

- `takeProfitPhase`：`ACCUMULATING / ARMED / TRIGGERED / COOLDOWN`。
- `cycleStartedAt`：当前止盈周期开始日。
- `cyclePeakNav`：当前周期启动后最高累计净值。
- `triggeredSignalId`：本周期唯一止盈信号。
- `cooldownStartedAt`：止盈交易确认时间。

激活新策略时初始化为 `ACCUMULATING`；停用或清仓分水岭时清理运行时字段。运行时字段不得由配置更新接口接收。

## 推荐模板

| FundCategory | 启动收益 | 高点回撤 | 浮盈收割 | 最低保留 | 单次上限 | 冷静期 |
|--------------|---------:|---------:|---------:|---------:|---------:|-------:|
| BROAD_BASE   |     0.15 |     0.06 |     0.50 |     0.50 |     0.20 |     10 |
| SECTOR       |     0.20 |     0.08 |     0.50 |     0.40 |     0.20 |     10 |
| ACTIVE       |     0.15 |     0.07 |     0.50 |     0.50 |     0.20 |     10 |
| MIXED        |     0.12 |     0.05 |     0.40 |     0.60 |     0.20 |     10 |

`GET /api/funds/{fundId}/strategies/recommendation` 返回当前基金类型及推荐值。创建请求缺失参数时由后端补齐推荐值；前端仍先查询推荐值用于解释和预填。服务端通过参数与推荐值逐项比较计算
`customized`，不信任客户端直接传标志。

## 状态机

```text
ACCUMULATING
  -- 整仓收益率达到启动线 --> ARMED（cyclePeakNav=currentNav，当天不卖）

ARMED
  -- 创新高 --> ARMED（更新 cyclePeakNav）
  -- 回撤达标且建议份额>0 --> TRIGGERED（绑定唯一 SignalLog）

TRIGGERED
  -- 交易 PENDING/未回应 --> TRIGGERED（不重复出可执行信号）
  -- 交易 CONFIRMED --> COOLDOWN（cooldownStartedAt=confirmTime）
  -- 交易 CANCELLED --> ARMED（保留周期高点，允许重新评估）

COOLDOWN
  -- 未满冷静期 --> COOLDOWN
  -- 到期且收益未达线 --> ACCUMULATING
  -- 到期且收益仍达线 --> ARMED（当天净值作为新周期高点，当天不卖）
```

逻辑止损在任何止盈阶段都先行判断；命中后可建议清仓，不受最低保留仓位和冷静期限制。

## 计算口径

### 整仓收益

沿用项目成本口径：

```text
holdingCost = costPerShare * holdingShares
marketValue = currentNav * holdingShares
floatingProfit = max(marketValue - holdingCost, 0)
overallReturn = floatingProfit / holdingCost
```

成本或持仓缺失、非正时不启动定投止盈。

### 建议卖出份额

```text
profitHarvestShares = floatingProfit * profitHarvestPercent / currentNav
singleSellCapShares = holdingShares * maxSingleSellPercent
retentionCapShares = holdingShares * (1 - minimumHoldingPercent)

suggestedShares = min(
  profitHarvestShares,
  singleSellCapShares,
  matureRedeemableShares,
  retentionCapShares
)
```

`matureRedeemableShares` 复用 `fund_lot`：逐 lot 用 `TradingCalendarService` 计算持有交易日，达到 5 个交易日的剩余份额可参与；事实持仓高于
open lot 合计的未跟踪 `ADJUST_IN` 份额视为成熟份额。实际确认继续由 `TransactionConfirmSupport` 按 FIFO 和真实赎回费率扣费。

## 跨层推进

- `SignalGenerationService` 在生成信号前调用生命周期服务处理启动、冷静期和高点；保存止盈 SignalLog 后绑定
  `triggeredSignalId`。
- `NavConfirmService` 和 `TransactionConfirmService` 在止盈卖出交易确认后调用同一个生命周期服务进入 `COOLDOWN`。
- `TransactionCancelService` 撤销止盈交易后调用生命周期服务恢复 `ARMED`。
- `StrategyConfigService.activate` 初始化新生效版本的运行时状态。

三条交易路径只调用生命周期服务，不复制状态判断。

## 参数校验

- 所有比例统一为正数。
- `profitActivationPercent`：`0 < x <= 1`。
- `stopLossPullbackPercent`：`0 < x < 1`。
- `profitHarvestPercent`：`0 < x <= 1`。
- `minimumHoldingPercent`：`0 <= x < 1`。
- `maxSingleSellPercent`：`0 < x <= 1`。
- `cooldownTradingDays`：`0 <= x <= 250`。
- 拒绝空请求和缺少基金类型；新增 `STRATEGY_PARAM_INVALID` 错误码。

最低保留与单次上限允许组合后由较小值生效，不强制 `maxSingleSellPercent <= 1-minimumHoldingPercent`，避免不必要限制。

## 数据库迁移与兼容

新增 V15：

- 给 `fund_strategy` 添加上述配置和运行时字段。
- 存量 `stop_loss_pullback_percent` 用 `abs` 规范为正数，实际触发幅度不变。
- 其余配置按关联基金的 `fund_category` 回填推荐值；存量策略标记 `customized=true`，避免被误认为系统新推荐。
- 存量状态与历史 SignalLog 保留；运行时阶段初始化为 `ACCUMULATING`，不删除旧策略版本。

保留 `stopLossPullbackPercent` 对外字段名，新增字段均为向后兼容扩展，不移除现有端点。

## 前端

- 新增 recommendation query hook。
- 策略表单打开时：编辑使用已有值；新建使用 recommendation。
- 显示推荐依据、参数解释、四个核心比例、单次上限和冷静期。
- 任一值偏离推荐时显示“已根据个人偏好调整”；提供“恢复类型推荐值”。
- 激活确认展示最终参数摘要。
- `PENDING_CALIBRATION` 展示改为“草稿”，兼容枚举名但不再使用“校准”词汇。

## 风险与回滚

- 风险：策略状态从无状态计算升级为持久化周期，需覆盖重跑、未回应、PENDING、确认和撤销路径。
- 风险：定投 lot 较多时逐行计算交易日；当前单基金规模有限，先复用已有查询，后续有性能证据再聚合优化。
- 回滚：代码可回滚；V15 只加列和回填，不删除历史列/数据，旧版本代码会忽略新增列。
