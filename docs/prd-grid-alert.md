# PRD：网格交易提醒（Grid Alert）

## 目标与用户价值

用户希望在**卖出/买入决策的最后窗口**得到网格式提示：以「最近一次真实成交价」为基准线，当盘中估值相对基准线上浮或下降超过设定比例时收到邮件，从而在 15:00 申购截止前决定加仓或减仓。

本需求交付一个**新的提醒规则种类 `GRID`**，复用现有提醒引擎的规则存储、幂等、邮件通道与前端表单，不新建模块、不新增状态表。

## 背景与已确认事实

以下事实均已通过代码勘察确认，不再需要人工确认：

| 事实 | 位置 |
| --- | --- |
| 提醒规则已支持多「种类」，`kind` 为 `VARCHAR(24)` 可扩展 | [AlertRuleKind.java](../../backend/src/main/java/com/fundpilot/backend/alerting/domain/alertrule/AlertRuleKind.java)、[V59](../../backend/src/main/resources/db/migration/V59__add_alert_rule_kind_and_suggestion_state.sql) |
| 非条件型规则的参数存 `alert_rule.parameters` JSON 列 | [AlertRuleJpaEntity.java:66](../../backend/src/main/java/com/fundpilot/backend/alerting/infrastructure/persistence/alertrule/AlertRuleJpaEntity.java#L66) |
| 幂等键：同一规则同一交易日最多一条 `SENT` | [V55](../../backend/src/main/resources/db/migration/V55__add_alert_rule_and_notification.sql) 的 `uq_alert_notification_rule_day_sent` |
| 评估任务当前固定在 14:30，无补跑 | [AlertEvaluationJob.java:20](../../backend/src/main/java/com/fundpilot/backend/alerting/adapter/scheduler/ruleevaluation/AlertEvaluationJob.java#L20) |
| 「成交净值」= `fund_transaction.nav`，仅真实买卖确认时写入 | [TransactionApi.java:129](../../backend/src/main/java/com/fundpilot/backend/accounting/adapter/api/transaction/TransactionApi.java#L129) |
| `ADJUST_IN/OUT` 与成本修正「录入即确认，不计算净值」，故 `nav` 为空 | [TransactionLedgerCommandHandler.java:33](../../backend/src/main/java/com/fundpilot/backend/accounting/application/command/transactionledger/TransactionLedgerCommandHandler.java#L33) |
| 盘中估值 `valuationNav` 仅在 `estimateStatus == AVAILABLE` 时有值；QDII 不拉取盘中估值 | [MarketRealtimeCache.java:508](../../backend/src/main/java/com/fundpilot/backend/marketdata/infrastructure/cache/realtimevaluation/MarketRealtimeCache.java#L508)、[EstimateStatus.java](../../backend/src/main/java/com/fundpilot/backend/marketdata/infrastructure/cache/realtimevaluation/EstimateStatus.java) |
| `currentFunds` 过滤掉 `CLEARED` 持仓的基金 | [PortfolioReturnQueryHandler.java:96](../../backend/src/main/java/com/fundpilot/backend/insights/application/query/portfolioreturn/PortfolioReturnQueryHandler.java#L96) |
| `evaluate()` 天然按「规则 × 交易日」幂等 | [AlertRuleEvaluationQueryHandler.java:148](../../backend/src/main/java/com/fundpilot/backend/alerting/application/query/ruleevaluation/AlertRuleEvaluationQueryHandler.java#L148) |
| 已有 `scheduled_job_status` 记录每任务 `last_finished_at` | [V57](../../backend/src/main/resources/db/migration/V57__add_scheduled_job_status.sql) |

## 需求

### R1 新增规则种类 `GRID`

在 `AlertRuleKind` 新增 `GRID("网格交易提醒")`，`suggestion()` 返回 `true`（走建议型链路，邮件含方向建议），`needsConditions()` 返回 `false`（判定由参数决定，不配置条件组）。

### R2 参数：`GridParams`

新增 `GridParams(upRatio, downRatio)`，两个独立比例，均取值 `(0, 1)`，校验方式对齐 [TakeProfitParams](../../backend/src/main/java/com/fundpilot/backend/alerting/domain/suggestion/TakeProfitParams.java) 的 `ratio(...)`。持久化复用 `alert_rule.parameters` JSON 列，编解码参照 `TakeProfitParamsJsonCodec` 新增 `GridParamsJsonCodec`。

### R3 基准线取数口径

基准线 = 该组合基金**最近一条 `nav` 非空且 > 0 的 `CONFIRMED` 流水**的 `nav`。

- 参与选取的来源：`INCREASE`、`DECREASE`、`INVEST`、`TRANSFER_IN`、`TRANSFER_OUT`（买卖都算）。
- 排除来源：`ADJUST_IN`、`ADJUST_OUT`、`COST_BASIS_RESET`（无成交净值）。
- 排序依据：`tradeDate`（缺失时回退 `confirmTime`），取最新一条。
- 无任何符合条件流水的基金：**跳过**，不发邮件、不回退到持仓成本单价。

### R4 判定口径

在评估时点（见 R6）对每只目标基金独立判定：

```
上线路 = 基准线 × (1 + upRatio)
下线路 = 基准线 × (1 - downRatio)
当前值 = 当日盘中估值 valuationNav
```

- 当前值 `>= 上线路` → 命中，方向为 `SELL`（建议卖出）。
- 当前值 `<= 下线路` → 命中，方向为 `BUY`（建议买入）。
- **净值取值限定为当日盘中估值**：仅当 `estimateStatus == AVAILABLE` 时参与判定；`STALE` / `TIMEOUT` / `PARSE_ERROR` / `UNAVAILABLE` / `NOT_ATTEMPTED` 一律跳过该基金，并在邮件正文与日志中标注「估值不可用」而非静默跳过。
- 上下两线互斥，同一天最多命中一个方向。

### R5 触发后的行为：每日比较 + 当日幂等

不引入穿越/复位状态机，不新增状态表。只要判定命中且该规则当日尚未成功发送，即发送；已发送则跳过（由现有 `alreadySent` 与 `uq_alert_notification_rule_day_sent` 双重保证）。

因此偏离持续期间会**每日发送一封**，这是本需求明确接受的行为。

### R6 触发时点与窗口内补跑

新增 `GridAlertEvaluationJob`：

- cron 覆盖 **14:50–14:59 每分钟一次**（`Asia/Shanghai`，`MON-FRI`），仅评估 `kind = GRID` 的规则。
- 窗口内每分钟重跑；第一条成功发送后，同规则当日幂等自动跳过，因此**窗口内补跑由现有幂等机制天然完成**，不需要扫描 `scheduled_job_status` 推断漏跑，也不新增状态表。
- 现有 `AlertEvaluationJob`（14:30）**不改动，且不评估 `GRID`**——避免在 14:30 估值不够准时提前发出误导信号。
- cron 与开关沿用 `fundpilot.alerting.*` 配置风格，新增可覆盖的配置项，默认值即上述窗口。

### R7 邮件内容与建议口径

命中邮件（复用现有 `AlertEmailGateway` 行结构）需写明：

- 基准净值及其来源（来自哪一天、哪一笔流水）；
- 上线路与下线路的具体数值；
- 当前盘中估值与偏离比例；
- 方向建议文案：命中上线 →「建议卖出」；命中下线 →「建议买入」。

**不计算具体份额或金额**：网格与回撤止盈是两套不同心智，不套用成熟可赎回份额与仓位上限口径。

### R8 作用范围与目标基金

- 支持 `GLOBAL` 与 `FUND` 两种范围；全局规则下每只基金各用自己的基准线。
- 未持仓基金仍参与判定（跌到下线本就是买入信号）。
- 已知限制（明确接受）：`CLEARED`（已清仓）基金被 `currentFunds` 过滤，不在判定范围内，因此清仓后收不到「跌到买回」信号。本需求不放开该过滤。

### R9 前端

在 [AlertRuleFormModal.jsx](../../frontend/src/pages/AlertRuleFormModal.jsx) 的 `KIND_OPTIONS` 增加「网格交易提醒」，新增两个比例输入（上浮比例、下降比例），并处理编辑回显与详情展示；后端 `AlertRuleController` 的 `Request` / `AlertRuleView` 增加网格参数字段与中文摘要（`AlertRuleText.summarize` 支持 `GRID`）。

## 验收标准

| 编号 | 验收项 |
| --- | --- |
| AC1 | 可创建 `kind=GRID` 的规则，参数为上浮/下降两个比例；比例越界（≤0 或 ≥1）被拒绝并返回 `ALERT_RULE_PARAMETER_INVALID` |
| AC2 | 基准线取最近一条 `nav>0` 的 `CONFIRMED` 流水；`ADJUST_*` 与成本修正流水被跳过（单测覆盖） |
| AC3 | 无符合条件流水的基金不触发、不发邮件，且日志标明原因 |
| AC4 | 当日盘中估值 `>= 上线路` 命中并给出「建议卖出」；`<= 下线路` 命中并给出「建议买入」（单测覆盖边界值） |
| AC5 | `estimateStatus != AVAILABLE` 的基金不触发，邮件/日志标注「估值不可用」；QDII 基金同样不触发 |
| AC6 | 同一规则同一交易日在 14:50–14:59 窗口内多次评估，最多只成功发送一封 |
| AC7 | 14:50 那次因故障未执行、14:53 恢复后任务仍能在窗口内完成当日评估并发送 |
| AC8 | 现有 14:30 的 `AlertEvaluationJob` 行为与发出时间不变，且不评估 `GRID` 规则 |
| AC9 | 网格规则纳入 `AlertRuleEvaluationQueryHandler.preview` 试算，返回基准线、上下线路与当前偏离 |
| AC10 | 前端可创建、编辑、回显网格规则，并在列表/详情显示中文摘要 |
| AC11 | 全局规则下多只基金各自用自己的基准线独立判定，互不影响 |

## 不在本次范围

- 自动下单或自动生成交易（系统边界：只通知，不做交易）。
- 网格状态机（穿越/复位、触发后基准线上移）。
- 具体建议份额/金额的计算。
- 放开 `CLEARED` 基金参与判定。
- 修改或合并现有 14:30 评估任务的时点。

## 技术备注

- **无需 Flyway 迁移**：`alert_rule.kind` 已是 `VARCHAR(24)`（`GRID` 仅 4 字符），`parameters` 已是 `TEXT`，两者均可直接承载新种类与新参数，不产生 DDL。
- `AlertRule` 聚合根当前只持有 `TakeProfitParams` 一个参数位，且 `apply(...)` 显式拒绝非 `TRAILING_STOP` 携带 `takeProfit`（[AlertRule.java:93](../../backend/src/main/java/com/fundpilot/backend/alerting/domain/alertrule/AlertRule.java#L93)）。需要为之新增 `gridParams` 字段并同步调整 `apply(...)`、`create(...)`、`rehydrate(...)`、`update(...)` 与 `signature()`（网格参数须参与口径签名，供全局规则去重）。
- 同步需要改动的参数翻译链路：`AlertRuleDraft.validate`（[AlertRuleDraft.java:27](../../backend/src/main/java/com/fundpilot/backend/alerting/application/command/rulemanagement/AlertRuleDraft.java#L27)）、`AlertRuleCommandHandler.RuleInput`、`AlertRulePersistenceMapper`（`parameters` 列读写）、`AlertNotificationDispatchCommandHandler.snapshot`（条件快照分支需容纳网格参数）。
- 建议将网格判定下沉为纯函数（类似 `TakeProfitPolicy`）以便单测，例如 `GridPolicy`：输入基准线、估值、上下比例，输出方向。
- 基准线取数需经 `TransactionApi`；应确认其现有 `Transaction` record 已暴露 `nav`（已确认暴露，见 [TransactionApi.java:129](../../backend/src/main/java/com/fundpilot/backend/accounting/adapter/api/transaction/TransactionApi.java#L129)），必要时在 gateway 侧新增一个方法而非改共享契约。
- `AlertFundFact` 已携带 `valuationNav` 与 `estimateStatus`，判定所需输入无需扩充事实结构。
- 邮件发送失败不占用当日额度，下次评估会重试（沿用现有语义）。
