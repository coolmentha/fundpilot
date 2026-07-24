# 技术设计

## 信号重跑

- 查询当日现有信号后，若任一信号已关联交易，保留原信号并结束本基金重跑。
- 未回应信号仍允许按现有覆盖语义重算。
- 回归测试覆盖逻辑止损已回应后重跑，不产生新 SignalLog。

## 定投日历与幂等

- 月定投候选计划日：今天日号小于计划日时取上月计划日，否则取本月计划日；仅当候选日至昨天均无交易日时在今天补执行。
- 周定投后端仅接受 1-5，前端移除周末选项。
- 幂等查询从 `dcaPlanId + PENDING + date range` 改为 `dcaPlanId + date range`，撤销视为用户明确放弃，不自动重建。
- `DcaPlanService` 在 create、update、activate 边界统一校验并清理无关日期字段。

## 交易发生日

- `ManualTransactionRequest` 增加可空 `tradeDate: Instant`。
- 前端使用 `YYYY-MM-DDT00:00:00+08:00` 提交，避免浏览器本地时区影响。
- V16 为 `fund_transaction` 增加 `trade_date TIMESTAMPTZ`，存量数据从 `created_date` 回填并增加基金+交易时间索引。
- `tradeDate` 保存业务发生时间，`createdDate` 保持审计语义；转换两腿复用同一值；未来日期拒绝。
- `TransactionTradeDate` 优先读取 `tradeDate`，仅存量空值回退 `createdDate`。

## 输入与展示

- 新增 `DCA_PLAN_INVALID` 和 `SIGNAL_OPERATION_VALUE_INVALID` 错误码。
- 新增 `SignalReason.NO_SELL_TRIGGER`，同步前端 label。
- 仅修正文案，不改变定时任务和行情刷新行为。

## Rollback

信号、定投和展示可独立回滚。交易日期代码回滚前需先确认 V16 已执行环境的数据兼容方案；迁移本身不删除旧字段，回滚应用仍可继续读取
`created_date`。
