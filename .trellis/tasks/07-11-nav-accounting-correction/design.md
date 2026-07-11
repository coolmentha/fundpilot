# 技术设计

## 净值语义

- 新增集中式净值选择 Module，明确返回交易单位净值和分析累计净值，禁止继续用无语义的 `navValue/currentNav` 在账目与分析间传递。
- `NavConfirmService`、`TransactionConfirmService`、初始持仓建仓和 `FundPnlService` 的事实账目路径改用单位净值。
- 策略前高、回撤、年线、MACD 与今日涨跌比例继续使用累计净值。

## 交易结算

- `TransactionConfirmSupport` 以 `tradeDate` 为统一业务日期。
- 买入：`fee = amount * feeRate`，`shares = (amount - fee) / unitNav`，lot 成本与基金成本均按 `amount / shares`。
- 卖出：FIFO lot 按交易发生日计算持有期，`gross = shares * unitNav`，`amount = gross - fee`。
- 两个确认入口只负责取交易日净值和状态门控，结算规则集中在同一 Module。

## 历史账本重建

- V17 新增一次性迁移状态表，并为现有账本写入待重建标记；空库不写待处理标记。
- 应用启动阶段同步执行重建，成功后写完成状态；异常抛出阻止应用正常启动，数据库事务整体回滚。
- 重建前读取 onboarding lot 的原始成本快照，以及历史 redemption rate 快照。
- 按 `tradeDate, id` 顺序重放 CONFIRMED 交易：买入建 lot，卖出/调减消耗 lot，转换转出产生的净额回填转入腿。
- 删除并重建 `fund_lot`、`fund_lot_redemption`；PENDING/CANCELLED 交易不参与事实持仓。
- 每只基金最终重新计算 `costPerShare`；清仓基金保持最近成本字段但不参与盈亏，后续再入场会覆盖。
- 重置策略运行周期，不修改止盈参数、preset 或 customized。

## 定投并发幂等

- V17 增加按 `dca_plan_id + 北京时间交易日` 的部分唯一索引。
- 建索引前按 `CONFIRMED > CANCELLED > PENDING`、再按最小 id 选择保留记录，其余软删。
- Job 保留应用层快速检查，但数据库唯一索引是最终一致性保证；唯一冲突按“本次未生成”处理。

## Rollback

- 部署前必须由现有运维流程完成数据库备份，本任务不修改 CD。
- V17 只新增迁移状态和唯一索引，不删除交易字段。
- 重建事务失败自动回滚；若业务核对失败，可恢复部署前数据库备份并回退应用 tag。
