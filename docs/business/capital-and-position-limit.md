# 定投预算与仓位提醒

## 业务目标与边界

月度定投预算回答“本月已经和预计会投入多少”，单基金仓位提醒回答“当前持仓是否过度集中”。两者都是提示，不是可用现金余额、自动调仓规则或交易拦截条件。

## 月度定投预算

- 用户配置可空的 `monthlyDcaBudget`；保存直接覆盖旧值，清空后不再比较预算。
- 摘要按北京时间自然月计算。已定投为所有未取消的 `INVEST`，包括手动/自动和 `PENDING`/`CONFIRMED`。
- 未来计划为当前 `EFFECTIVE` 且 `enabled=true` 的计划在本月剩余实际执行日的金额。已为同一计划创建任意状态交易的日期不得重复预测。
- 日计划、周计划和月计划复用 DCA 执行日规则；当天仅在 14:55 前算未来，月计划连续休市后按实际跨月执行日期归属。
- 未设置预算时仍显示已定投、未来计划和预计定投；设置后才显示分段进度、剩余或超额。

```text
本月预计定投 = 本月已定投 + 本月未来计划
预算剩余 = max(月度预算 - 本月预计定投, 0)
预计超出 = max(本月预计定投 - 月度预算, 0)
```

超额只显示红色进度和明确文字，不能暂停计划、撤销交易或阻止确认。

## 单基金仓位提醒

- 每只基金保存 `positionWarningEnabled` 和 `positionWarningRatio`，默认开启、30%，提醒线允许 1% 至 100%。
- 当前占比只使用已确认事实持仓的当前市值：`该基金持仓市值 / 全部基金持仓市值`。
- 任一已持仓基金的当前市值未知时，整组仓位占比都展示未知；不得按剩余可用基金重算一个部分组合的 100%。
- 关闭后仍显示当前比例，但不显示超线告警；不实现定投后的预计占比。

## 非拦截约束

`INCREASE`、`TRANSFER_IN`、`INVEST`、初始持仓、转换和净值确认均不得读取月度预算或仓位提醒字段。确认交易仍只受金额/份额有效性、交易日净值、事实持仓、FIFO lot 和状态机约束。

## 失败与错误

| 场景 | 结果 |
| --- | --- |
| 月度预算为非正或超过金额精度 | `MONTHLY_DCA_BUDGET_INVALID` |
| 仓位提醒线不在 `(0, 1]` | `POSITION_WARNING_RATIO_INVALID` |
| 预算超额或仓位超提醒线 | 仅展示提示，不抛业务错误 |

## 实现与验证入口

- 实现：[DcaBudgetSummaryService](../../backend/src/main/java/com/fundpilot/backend/dca/service/DcaBudgetSummaryService.java)、[DcaScheduleService](../../backend/src/main/java/com/fundpilot/backend/dca/service/DcaScheduleService.java)、[UserConfigService](../../backend/src/main/java/com/fundpilot/backend/user/service/UserConfigService.java)
- 测试：[DcaBudgetSummaryServiceTest](../../backend/src/test/java/com/fundpilot/backend/dca/service/DcaBudgetSummaryServiceTest.java)、[DcaScheduleServiceTest](../../backend/src/test/java/com/fundpilot/backend/dca/service/DcaScheduleServiceTest.java)、[FundServiceTest](../../backend/src/test/java/com/fundpilot/backend/fund/service/FundServiceTest.java)
- 相关决策：[ADR-0021](../adr/0021-dca-budget-and-position-warnings.md)、[ADR-0019](../adr/0019-unit-nav-for-accounting.md)
