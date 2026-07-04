# 定投配置:基金定投计划自动买入

## Goal

用户可为基金配置定投计划(金额/频率/定投日),系统在定投日自动生成 INVEST 交易(PENDING),经净值确认流程落库份额。定投不止盈——止盈交给基金绑定的移动止盈/逻辑止损信号(卖出纪律与定投解耦)。平台从"只看行情+手动交易"升级为"行情+自动定投+卖出纪律辅助"。

## Background

- 金字塔加仓已退场(v0.4.0),信号引擎只剩 SELL(逻辑止损+移动止盈)。
- 定投在代码里原本只是交易来源标签 `INVEST`(`FundTransactionSource.java:10`),已端到端打通(录入→净值确认→成本加权),但无自动定投机制。
- 用户要"配置一次,每天/周/月自动买入",不经过信号系统(定投不是建议,是自动执行)。
- 现有净值确认时序:交易先 PENDING(nav=null)→ DailyNavConfirmJob 20:00-22:00 落当日净值 → NavConfirmJob 确认 PENDING 交易。
- SignalLog 唯一索引 `uq_signal_log_daily(fund_id, signal_date::date)`——定投不走 SignalLog,绕开冲突。

## Requirements

### R1 定投计划实体
- 新建 `fund_dca_plan` 表:fund_id(FK)、enabled(布尔)、amount(每次金额)、frequency(WEEKLY/MONTHLY 枚举)、day_of_week(int 1-7,周定投用)、day_of_month(int 1-28,月定投用)、status(DRAFT/EFFECTIVE 沿用策略状态机风格)、公共字段(id/version/created_date/updated_date/deleted_date)。
- 一只基金最多一份 EFFECTIVE 定投计划(唯一约束 `uq_fund_dca_plan_effective`)。
- `FundDcaPlanEntity` + `FundDcaPlanRepository` + `FundDcaPlanView` + `DcaPlanRequest`。

### R2 定投计划 CRUD
- `DcaPlanController`:POST 创建、PUT 更新草稿、POST activate、POST retire、GET 列表、GET active。
- `DcaPlanService`:状态机 DRAFT → activate → EFFECTIVE → retire → DRAFT。createDraft/updateDraft/activate/retire/listByFund/findActive。
- 镜像现有 StrategyConfigService 的 CRUD 模式。

### R3 自动定投 Job
- 新建 `DcaSuggestionJob`(cron `0 55 14 * * MON-FRI`,紧跟 SignalGenerationJob 之后)。
- 遍历所有 EFFECTIVE 定投计划,判定"今天是否是定投日":
  - 先 `TradingCalendarService.isTradingDay(today)` 过滤非交易日。
  - 周定投:today 的 day-of-week == plan.day_of_week。
  - 月定投:today 的 day-of-month == plan.day_of_month;若计划日非交易日,顺延到下一个交易日(节假日漂移)。
- 命中则生成 FundTransaction(source=INVEST, amount=plan.amount, shares=null, nav=null, status=PENDING, signalLogEntity=null)。
- 防重复:若该基金今日已有 source=INVEST 且 createdByDca 的 PENDING 交易,跳过(幂等)。需在 FundTransaction 加 `dca_plan_id` 可空字段标记来源,或查同日同 source 交易去重。

### R4 净值确认时序调整
- `NavConfirmJob` cron 从 `0 0 21 * * MON-FRI` 改为 `0 0 3 * * MON-FRI`(次日凌晨 3 点确认昨天的 PENDING 流水)。
- 语义:凌晨 3 点确认的是"下单日已公布净值"的 PENDING 交易——当晚 20:00 DailyNavConfirmJob 已落下单日净值,次日凌晨 3 点 NavConfirmJob 用该净值算份额并 CONFIRMED。
- NavConfirmService 逻辑不变(查 PENDING 交易 → 查下单日净值 → 算 shares → CONFIRMED → 加权 costPerShare)。

### R5 前端定投计划 tab
- `FundDetailPage` 新增第 5 个 tab "定投计划"。
- `FundDcaTab.jsx`:列表展示(enabled/amount/frequency/day/status)+ 新建/编辑弹窗 + 激活/停用按钮。镜像 FundStrategyTab 结构。
- `DcaPlanFormModal.jsx`:表单字段 amount(InputNumber)、frequency(Select WEEKLY/MONTHLY)、day_of_week/day_of_month(按 frequency 动态显示)。
- api hooks:useDcaPlans/useActiveDcaPlan/useCreateDcaPlan/useUpdateDcaPlan/useDcaPlanAction。

### R6 止盈
- 定投计划不止盈。基金若绑定了 EFFECTIVE 策略(stopLossPullbackPercent),移动止盈/逻辑止损信号照常产生,用户确认 SELL 信号即止盈减仓。
- 定投与卖出纪律完全解耦:定投只管自动买入,卖出交给信号引擎。

## Acceptance Criteria

- [ ] AC1 `fund_dca_plan` 表存在(V11 迁移),一只基金最多一份 EFFECTIVE 计划。
- [ ] AC2 用户可创建/编辑/激活/停用定投计划,前端定投计划 tab 可操作。
- [ ] AC3 DcaSuggestionJob 在定投日 14:55 生成 source=INVEST 的 PENDING 交易,amount=计划金额。
- [ ] AC4 非定投日不生成交易;非交易日不跑 Job。
- [ ] AC5 月定投计划日遇节假日顺延到下一个交易日。
- [ ] AC6 同日不重复生成(幂等:已有 PENDING INVEST 交易则跳过)。
- [ ] AC7 NavConfirmJob 凌晨 3 点确认昨日 PENDING 交易,用下单日净值算份额,CONFIRMED 后加权 costPerShare。
- [ ] AC8 `mvn test` 全绿(CI Testcontainers);`npm run build` 通过。
- [ ] AC9 CONTEXT.md 新增"定投计划"章节;ADR-0016 记录决策。

## Out of Scope

- 定投止盈阈值(目标收益率)——本期止盈完全交给移动止盈信号。
- 定投暂停/恢复(除 retire 外的临时暂停)——本期只有 EFFECTIVE/retire 两态。
- 定投收益统计/回测——本期不做。
- 批量定投计划管理页——本期只在基金详情页 per-fund 配置。

## Open Questions

- 无(需求已通过 AskUserQuestion 澄清:自动买入不走信号、止盈交移动止盈、凌晨3点确认)。
