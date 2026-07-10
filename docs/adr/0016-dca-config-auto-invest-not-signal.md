# 定投配置:自动执行非信号,止盈交移动止盈

平台定位为"实时行情工作台,定投止盈为辅"。金字塔加仓退场(ADR-0015)后,买入完全由用户手动决定。
用户提出"配置一次、系统按周期自动买入"的定投需求。本 ADR 记录定投作为**自动执行机制**(非信号)的决策,
以及与移动止盈的解耦边界。

## 决策

定投计划(`FundDcaPlanEntity`)是独立于信号引擎的自动执行实体。用户配置金额/频率/定投日后,
`DcaSuggestionJob` 在定投日 14:55 直接生成 `source=INVEST` 的 PENDING 交易,绕开 SignalLog 与 `evaluateSignal`。
止盈(移动止盈)仍由基金绑定的 `FundStrategyEntity` 信号独立触发,两者解耦:定投负责持续买入,止盈负责减仓退出。

## Considered Options

- **A. 定投走信号引擎(BUILD/ADD 信号)〔否决〕**:定投命中时生成 BUILD/ADD 信号,再由用户或自动确认成交易。
  否决——信号是"建议",定投是"执行",语义不同。定投用户已明确买入意图,无需信号再判一次;且金字塔退场后信号引擎仅 SELL,
  重新引入买入信号违背 ADR-0015。
- **B. 定投与移动止盈合并为一个"策略"〔否决〕**:策略同时管买入(定投)和卖出(止盈)。否决——买入(定投)是确定性周期执行,
  卖出(止盈)是条件触发,触发逻辑、状态机、参数都不同,合并会让策略实体语义过载。保持两个独立实体:
  `FundDcaPlanEntity`(买入) + `FundStrategyEntity`(卖出)。
- **C. 定投自动执行 + 移动止盈信号解耦〔已采纳〕**:定投直接生成 INVEST 交易,止盈由信号引擎独立触发。
  两者唯一耦合点是同一基金的持仓(定投增加持仓,止盈减少持仓),通过 `FundTransactionEntity` 账目自然聚合,无需显式协调。

## Consequences

- **正面**:定投执行路径简单(无信号判定开销),幂等清晰(`dcaPlanId` + 日期窗口去重);止盈逻辑不被定投干扰,
  信号引擎保持 SELL-only;用户可单独启用/停用定投而不影响止盈纪律。
- **负面**:`FundTransactionEntity` 新增 `dcaPlanId` 列(V11 迁移)用于幂等去重——非空约束松(手动 INVEST 交易该列为 null),
  去重仅对定投生成的交易生效。月定投日封顶 28(避开月末交易日缺失),>28 不支持,需用户接受月初定投替代。
- **时序**:`DcaSuggestionJob` 14:55 下单(PENDING) → `DailyNavConfirmJob` 20:00 拉净值 → `NavConfirmJob` 次日 03:00 确认
  (cron 从 21:00 改 03:00,确认"之前生成的"流水,单日定投流水在 14:55 已生成,次日 3 点确认时净值已落地)。
- **交易日历约定**:Job 查询 `TradingCalendarService` 统一调用 `ChinaTradingDate.toUtcDate(now)`，把北京时间自然日映射成
  UTC 00:00 DATE 标签；不可直接用 Asia/Shanghai 午夜 Instant（会与 UTC-midnight 存储的日历行错位一天）。
- **后续**:定投暂不支持按净值估值动态调额(智能定投),本期固定金额;如需可在 `FundDcaPlanEntity` 加策略字段扩展。
