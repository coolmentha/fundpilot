# 金字塔加仓退场,移动止盈解耦为独立卖出纪律

平台已转向"实时行情工作台,定投止盈为辅"定位。金字塔择时加仓机制(建仓/四档加仓/计划总仓位)不再符合产品方向。
本 ADR 记录移除金字塔加仓、将移动止盈从金字塔档位状态解耦为独立"按回落分档减仓"规则的决策。

`plannedTotalAmount`(fund 表)是金字塔加仓的金额分母(建仓额=`plannedTotalAmount×0.10`,加仓额=`plannedTotalAmount×tierRatio`)。
移动止盈原实现深度耦合金字塔档位:从 `tier1~4AddedAt` 找可卖档、按 `tierAddShares`/`buildShares` 卖出。删金字塔后移动止盈无法独立存活,需解耦。
代码里无独立"定投策略"实体——"定投"只是交易来源标签 + 回测对照基准。本次只做"删金字塔 + 移动止盈解耦",定投配置属未来功能。

## Considered Options

- **A. 删金字塔 + 移动止盈解耦为按回落分档减仓(已采纳)**:删 `plannedTotalAmount` + tier1~4 字段 + BUILD/ADD 信号 + 回测/寻优 + 硬约束;
  移动止盈改为"回落 n×stopLossPullbackPercent 卖 holdingShares×(n/4)"(1×卖1/4,4×全卖);逻辑止损 ACTIVE 分支去掉 weeklyCoolDown 条件;
  7天持有期保留(起算点改为最近 CONFIRMED 交易时间)。信号引擎从 BUILD/ADD/SELL 三类瘦身为仅 SELL。
- **B. 保留 BUILD 信号**:建仓信号(年线上方+向上+60日新高)作为轻量买入提示,金额改为固定值。否决——BUILD 金额语义依赖 plannedTotalAmount,
  改固定值是新增设计且与"行情为主、买入用户自主"定位冲突。
- **C. 移动止盈单一阈值全卖**:回落≥阈值全清仓。否决——用户选择保留分档减仓的渐进止盈语义(回落越深卖越多),避免一次清仓的二元决策。
- **D. 保留回测/寻优,只删金字塔加仓**:否决——回测/寻优深度依赖 plannedTotalAmount(DCA 基准+门控)和 tier 参数(寻优网格对象),
  金字塔没了它们失去对象,保留需大幅重设计且无明确收益。

## Consequences

- **正面**:信号引擎简化(只 SELL),CapitalContext 10→4 字段,fund_strategy 14→2 业务列,策略状态机无 calibrate 流转。
  买入完全由用户手动/定投决定,平台专注卖出纪律辅助,符合行情工作台定位。
- **负面**:V10 迁移 DROP COLUMN 不可逆(金字塔档位状态数据丢失,但已无业务价值)。ACTIVE 逻辑止损放宽(去周跌幅条件,可能更易触发——
  可接受,破年线+MACD绿柱已足够表达趋势死亡)。
- **存量兼容**:SignalReason 枚举 BUILD/ADD/REBALANCE 等值 @Deprecated 保留供存量 SignalLog 反序列化;signal_log 表不动,历史信号可读;
  StrategyParamStatus 枚举 CALIBRATED/CALIBRATION_FAILED 保留供存量数据(不再产生新流转)。
- **后续**:定投配置(金额/频率/周期)属未来新功能,本次不建。
