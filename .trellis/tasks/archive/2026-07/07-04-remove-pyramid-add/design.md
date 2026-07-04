# Design: 移除金字塔加仓机制,移动止盈解耦为独立卖出纪律

## 架构与边界

本次是"减法重构":删除金字塔加仓机制,保留并解耦卖出纪律。信号引擎从"BUILD/ADD/SELL 三类"瘦身为"仅 SELL",CapitalContext 从 10 字段瘦身为 4 字段。

### 改动前信号引擎结构
```
evaluateSignal
├─ decideAction
│  ├─ PENDING_HOLDING → decideBuild (建仓, plannedTotalAmount×0.10)
│  └─ HOLDING
│     ├─ decideSell
│     │  ├─ checkLogicBrokenStopLoss (读 weeklyCoolDownThreshold for ACTIVE)
│     │  └─ checkTrailingStop (读 tier1~4AddedAt + tierAddShares + buildShares)
│     └─ decideAdd (四档加仓, plannedTotalAmount×tierRatio)
├─ clearTiersOnRebound (金字塔反弹清空)
├─ addWarnings (加仓专属)
└─ applyHardConstraints (BUILD/ADD 硬约束, 读 singlePositionPct/categoryPositionPct)
```

### 改动后信号引擎结构
```
evaluateSignal
├─ decideAction
│  ├─ PENDING_HOLDING → NONE (NO_STRATEGY)  // 不再给建仓建议
│  └─ HOLDING
│     └─ decideSell
│        ├─ checkLogicBrokenStopLoss (ETF 分支不变; ACTIVE 分支去掉 weeklyCoolDown 条件)
│        └─ checkTrailingStop (重写: holdingShares × n/4, 不读 tier)
└─ applyMinHoldDays (保留, 7 天持有期)
```

## 数据契约变更

### CapitalContext (10 → 4 字段)
```java
// 改动前
record CapitalContext(peakNav, holdingPeriodPeakNav, singlePositionPct,
    categoryPositionPct, totalEquityAmount, plannedTotalAmount,
    buildShares, tierAddShares, holdingShares, lastBuyConfirmTime)

// 改动后
record CapitalContext(peakNav, holdingPeriodPeakNav,
    holdingShares, lastBuyConfirmTime)
```
删 6 字段:plannedTotalAmount / buildShares / tierAddShares(BUILD/ADD 专属)+ singlePositionPct / categoryPositionPct / totalEquityAmount(硬约束专属,硬约束随 BUILD/ADD 删)。

### FundStrategyEntity (14 → 2 业务字段)
保留:`status`(策略版本状态机)、`stopLossPullbackPercent`(移动止盈阈值)。
删 12 字段:tier1~4Drawdown/Ratio/AddedAt。删 weeklyCoolDownThreshold。

### FundEntity
删 `plannedTotalAmount`。

### SignalResult / SignalLogEntity
- `triggerTier` 字段:移动止盈新逻辑仍产生 triggerTier(1~4 表回落档数),保留用于展示"第几档止盈"。BUILD/ADD 不再产生,历史数据保留。
- `coefficient` 字段:原 BUILD/ADD 调节系数,移动止盈不用 → 可保留为 null 或删字段。**决策:保留字段(避免 schema 迁移),值恒 null。**

## 移动止盈新算法

```java
// checkTrailingStop 重写
BigDecimal pullback = peak.subtract(currentNav).divide(peak, MATH);
BigDecimal threshold = strategy.getStopLossPullbackPercent();
if (threshold == null || threshold.signum() <= 0) return null;

// 回落 n×threshold,n 从 4 降到 1,取最大 n
int triggerTier = 0;
for (int n = 4; n >= 1; n--) {
    if (pullback.compareTo(threshold.multiply(BigDecimal.valueOf(n), MATH)) >= 0) {
        triggerTier = n;
        break;
    }
}
if (triggerTier == 0) return null; // 未达止盈阈值

// 卖出份额 = holdingShares × (triggerTier / 4)
BigDecimal ratio = BigDecimal.valueOf(triggerTier).divide(BigDecimal.valueOf(4), MATH);
BigDecimal shares = capital.holdingShares().multiply(ratio, MATH);
Measure measure = new Measure(shares, MeasureUnit.SHARE);
return new SignalResult(SignalType.SELL, triggerTier, null, measure,
    SignalReason.TRAILING_STOP, warnings, List.of());
```

语义:回落越深卖越多。1×阈值只卖 1/4(轻止盈),4×阈值全卖(清仓)。不依赖任何金字塔档位状态。

## 逻辑止损 ACTIVE 分支调整

```java
// 改动前 ACTIVE 分支:破年线 + MACD绿柱扩大 + 单周跌幅>weeklyCoolDownThreshold
// 改动后 ACTIVE 分支:破年线 + MACD绿柱扩大 (去掉第三条件)
```
理由:weeklyCoolDownThreshold 随金字塔删除;ACTIVE 基金无跟踪指数,第三条件原本用周跌幅代理趋势死亡,但前两条件(破年线+MACD绿柱扩大)已足够表达趋势死亡。与 ETF 分支的"放量下跌"第三条件不对称是可接受的——主动基金本就无量能数据。

## 回测/寻优退场

删除清单(整文件删):
- `strategy/service/DefaultStrategyBacktestService.java`
- `strategy/service/StrategyOptimizeService.java`
- `strategy/service/support/BacktestSimulator.java`
- `strategy/service/support/BenchmarkCalculator.java`
- `strategy/service/support/BacktestParams.java`
- `strategy/service/support/OptimizeParamRanker.java`
- `strategy/service/support/OptimizeGridGenerator.java`
- `strategy/service/support/OptimizeParams.java`
- `fund/service/support/DefaultTierTable.java`
- `fund/service/support/DefaultCoolDownTable.java`
- `fund/service/support/TierDefaults.java`
- `fund/service/support/HardConstraintChecker.java`(随 BUILD/ADD 删)
- `fund/service/support/HardConstraintConfig.java`(随 BUILD/ADD 删)
- `fund/service/support/CoefficientTable.java` / `CoefficientCombiner.java`(BUILD/ADD 调节系数,删)

`StrategyConfigService`:
- 删 `calibrate` 方法及 `run` 调用
- 删 `applyRequest` 里 tier/weeklyCoolDown 字段 set(只剩 stopLossPullbackPercent)
- `StrategyParamStatus` 枚举:PENDING_CALIBRATION/CALIBRATION_FAILED/CALIBRATED 保留(存量数据),但 createDraft 后不再走 calibrate 流转,直接 → EFFECTIVE(或保留 PENDING→手动 EFFECTIVE)。

**决策**:calibrate 流转简化为 createDraft(PENDING_CALIBRATION)→ 手动 activate → EFFECTIVE。不再有回测校验门。前端"校准"按钮删除,"生效"按钮保留。

## DB 迁移 V10

```sql
-- V10__drop_pyramid_add_columns.sql
ALTER TABLE fund DROP COLUMN IF EXISTS planned_total_amount;

ALTER TABLE fund_strategy
    DROP COLUMN IF EXISTS tier1_drawdown,
    DROP COLUMN IF EXISTS tier2_drawdown,
    DROP COLUMN IF EXISTS tier3_drawdown,
    DROP COLUMN IF EXISTS tier4_drawdown,
    DROP COLUMN IF EXISTS tier1_ratio,
    DROP COLUMN IF EXISTS tier2_ratio,
    DROP COLUMN IF EXISTS tier3_ratio,
    DROP COLUMN IF EXISTS tier4_ratio,
    DROP COLUMN IF EXISTS tier1_added_at,
    DROP COLUMN IF EXISTS tier2_added_at,
    DROP COLUMN IF EXISTS tier3_added_at,
    DROP COLUMN IF EXISTS tier4_added_at,
    DROP COLUMN IF EXISTS weekly_cool_down_threshold;
```

存量 signal_log 表不动(triggerTier/coefficient 列保留,历史数据可读)。

## 兼容性与回滚

- **存量数据**:fund_strategy 的 tier 列删除后,历史 SignalLog 的 triggerTier 仍可读(存在 signal_log 表,不删)。前端展示历史信号时 BUILD/ADD reason 有 label。
- **回滚**:V10 是 DROP COLUMN,回滚需 V11 重建列(数据已丢)。**本迁移不可逆**,需在 PR review 时确认。考虑到金字塔已退场、数据无业务价值,可接受。
- **API 契约**:FundCreateRequest/FundView 删 plannedTotalAmount——前端必须同步部署,否则旧前端发 plannedTotalAmount 会被后端忽略(record 反序列化容错),新前端不发也不影响。

## 前端改动

- `FundsPage.jsx`:删 plannedTotalAmount 表单项/列/初始值/回填;基金表单只剩搜索框+类型(+可选入仓市值)。
- `FundDetailPage.jsx`:删"计划仓位"Descriptions.Item。
- `DashboardPage.jsx`:删"计划仓位"列。
- `FundStrategyTab.jsx`:删 tier 档位卡、回测对照列、校准按钮;只留 stopLossPullbackPercent 展示+生效状态。
- `StrategyFormModal.jsx`:FIELDS 数组只剩 stopLossPullbackPercent 一项。
- `FundSignalTab.jsx`/`SignalsPage.jsx`:triggerTier 展示保留(移动止盈仍产生);BUILD/ADD 历史 reason label 保留。
- `constants.js`:SignalReason labels 保留全部(历史信号可读);errorTitles 无需改。

## 风险与权衡

1. **ACTIVE 逻辑止损放宽**:去掉周跌幅条件,ACTIVE 基金逻辑止损可能更易触发(只看年线+MACD)。可接受——趋势死亡的核心信号是破年线+MACD绿柱,周跌幅是辅助。
2. **calibrate 流转简化**:不再回测校验,策略直接生效。可接受——回测本身是金字塔寻优配套,金字塔没了回测无意义。
3. **V10 不可逆**:DROP COLUMN 丢数据。可接受——金字塔档位状态已无业务价值。
4. **HardConstraintChecker 整体删除**:单只/单类占比上限检查随 BUILD/ADD 消失。可接受——平台不再给买入建议,占比约束无意义。
