# 智能定投技术设计

## 设计目标

- 保留固定金额计划的现有执行链、账目语义和幂等约束。
- 智能计划只改变命中周期后的本期金额决策，不接入外部交易平台。
- 执行时只读取本地市场数据，禁止在数据库事务内发起远程请求。
- 规则使用单个纯计算策略类和版本常量，不为三种策略建立接口、工厂或配置中心。

## 数据模型

### investment_plan

通过 V49 扩展现有表：

- `amount_strategy VARCHAR(32) NOT NULL DEFAULT 'FIXED'`
- `reference_index_code VARCHAR(32)`
- `moving_average_days INTEGER`

`amount` 继续表示基础金额。存量行依靠默认值保持 `FIXED`。数据库约束只允许
`FIXED / LOW_VALUATION / MOVING_AVERAGE / CHANGE_RATE`，均线周期只允许
`180 / 250 / 500`。

### index_valuation

新增指数估值历史表，保存 `index_code / trade_date / pe_ratio / source`，以
`index_code + trade_date + source` 唯一。数据来自中证指数公司专用历史估值接口
`/perf/indexCsiDsPe`，其 `peg` 字段在本地明确映射为该来源的 PE 观测值。

不能复用现有 K 线 `/perf/index-perf` 响应中的同名字段：调研已验证两条接口在同日数值和
行数不同，混用会破坏分位口径。估值抓取仍挂在现有行情指标刷新流程：本地无数据时拉取
指数可用全历史，之后从最近数据日起增量更新；不新增供应商、独立定时任务或执行时远程调用。

### investment_plan_execution

新增不可变执行记录表，最小字段如下：

- 计划、北京时间业务日、策略、规则版本
- `EXECUTED / SKIPPED` 结果和原因码
- 基础金额、实际金额、扣款率
- 数据日期、参考指数、均线周期
- 主指标和辅助指标（PE 分位/均线偏离/成本涨跌幅及 10 日振幅）

唯一键为 `investment_plan_id + business_date`。该表解决智能策略跳过时没有交易流水，
但仍需同日幂等、月计划本期已处理和原因可查询的问题。当前 UI 只展示每个计划最近一条记录；
完整历史查询页不在本期范围内。

## 领域与模块边界

- `InvestmentPlan` 增加策略类型、参考指数和均线周期，并在创建/更新时校验组合。
- 新增一个 `SmartInvestmentAmountPolicy` 纯计算类，以 `switch` 处理三种智能策略。
  不建立单实现接口、策略工厂或运行时规则配置。
- `PlanPortfolioFundGateway.PortfolioFund` 增加 `fundProductId` 和基金基准指数，复用
  Portfolio 与 ProductCatalog 的公开 API。
- 新增一个执行事实 Gateway，复用 Accounting 的 `PositionApi`、MarketData 的
  `PublishedNavApi`、`IndexKlineApi` 与新增的 `IndexValuationApi`，向应用层返回一次性本地事实快照。
- InvestmentPlan 模块允许依赖 `productcatalog::api`；其余跨模块调用仍走现有公开 API。

## 规则快照

规则版本固定为 `ALIPAY_2025_06_V1`，具体档位以 `prd.md` 为准。

### 低估

1. 使用基金基准指数，不接受低估策略单独改选指数。
2. 仅取同一 `source`、业务日前且 `pe_ratio > 0` 的本地样本，最新样本即实际数据日期。
3. PE 分位为 `PE <= 当前 PE 的样本数 / 有效样本总数 * 100`。
4. 分位不高于 30% 时按 100% 执行；否则记录 `VALUATION_NOT_LOW` 并跳过。
5. 没有任何有效 PE 时记录 `VALUATION_UNAVAILABLE` 并跳过。

### 均线

1. 使用计划参考指数；未显式选择时保存基金基准指数。
2. 仅取业务日前的收盘价，至少需要所选均线周期数量的有效 K 线。
3. 近 10 日振幅按 `最高收盘价 / 最低收盘价 - 1` 计算。
4. 缺足够 K 线时记录 `INDEX_KLINE_UNAVAILABLE` 并跳过。

### 涨跌幅

1. 使用最新已发布基金单位净值和 Accounting 当前 `costPerShare`。
2. 涨跌幅按 `(单位净值 - 平均成本) / 平均成本` 计算。
3. 缺净值或成本时分别记录 `NAV_UNAVAILABLE / COST_UNAVAILABLE` 并跳过。

智能实际金额按 `基础金额 * 扣款率` 计算并以 `HALF_UP` 保留两位小数。固定金额路径不增加
该舍入步骤，避免改变存量行为。

## 执行流程与幂等

1. `InvestmentPlanExecutionCommandHandler` 保留交易日和周期判断。
2. 复用 `findTrackedForExecution` 锁定组合基金，串行化同一基金的并发执行。
3. 固定金额计划继续直接调用现有 `PlanTransactionGateway.createPending`。
4. 智能计划先检查当日执行记录；已有记录立即返回。
5. 从本地 Gateway 加载事实并由纯策略类计算结果。
6. 跳过结果只写执行记录，不创建交易。
7. 执行结果先创建 `INVEST/PENDING`，再写执行记录；二者在同一事务中提交。
8. Accounting 的现有同计划同北京时间自然日唯一索引继续作为交易最终防重；新执行表唯一键作为
   智能决策最终防重。

月计划“本月是否已处理”和本月预测同时合并 Accounting 交易 occurrence 与智能执行记录日期。
因此低估或数据缺失导致的跳过不会在次日重复补投。日/周计划的既有周期语义不变。

## 查询与预算

- 计划列表批量查询最近执行记录，避免逐计划查询。
- `PlanView` 增加策略配置、单期金额区间和最近决策字段。
- 预算主金额 `futureAmount/projectedAmount` 继续按基础金额计算。
- 预算摘要增加未来及预计的最小/最大金额；固定、低估、均线、涨跌幅分别按
  `100%-100% / 0%-100% / 60%-210% / 50%-200%` 计算。
- 已产生的交易仍以 Accounting 中的实际金额为准，计划编辑不会回写交易。

## 前端

- `DcaPlanFormModal` 使用四段式模式选择，默认固定金额。
- 均线模式显示参考指数与 `180/250/500` 周期选择，默认带出基金基准指数和 250 日。
- 缺基准指数时禁用低估模式；后端仍做同等校验。
- 计划列表显示基础金额、金额范围和最近决策；跳过原因使用固定中文映射，不直接展示内部原因码。
- `DcaBudgetOverview` 保留现有主金额，同时展示智能计划未来区间。

## 兼容、发布与回滚

- V49 是前向 Flyway 迁移：只新增可空列、带默认值列和新表，不删除旧字段。
- 旧版本应用可忽略新增列和新表；应用回滚后固定计划仍可执行，但新建智能计划会被旧代码当基础金额固定执行。
  因此发布回滚时必须先暂停新增智能计划或回滚相关计划为 `FIXED`。
- 代码发布前以可恢复数据库备份验证 V1 到 V49 全迁移链；迁移失败必须阻止启动。

## 主要风险

- 支付宝未公开算法 API，规则只能作为 2025-06 截图快照，不能宣称实时同步。
- 中证专用估值响应字段名为 `peg` 且没有公开计算公式，本地必须保留来源标识；上线前用多个指数样本核验数值合理性，不与 K 线接口或其他 PE 口径拼接。
- 自定义参考指数若本地没有足够 K 线会跳过，不在执行事务内临时访问外部源。
