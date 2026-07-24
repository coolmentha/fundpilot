# v0.9 技术设计

## 数据来源

- 使用 `CONFIRMED` 的 `FundTransactionEntity`、`FundLotEntity` 和 `FundLotRedemptionEntity` 实时聚合。
- 当前持仓市值复用 `FundPnlService` 的单位净值口径。
- 不新增收益快照表或持久化派生字段。

## 计算边界

- `INCREASE/INVEST`：外部投入；申购费计入成本和费用。
- `DECREASE`：外部赎回净额；FIFO lot 成本用于已实现盈亏。
- 关联 `TRANSFER_OUT/TRANSFER_IN`：基金明细分别记录，组合层通过 `relatedTransactionId` 抵消本金流。
- 初始持仓：按成本单价乘份额作为历史投入基准，不作为当天现金流。
- `ADJUST`、`PENDING`、`CANCELLED` 不生成组合现金流收益。

## API/UI

- 扩展现有组合摘要接口返回总投入、总赎回、总费用、已实现收益、未实现收益和累计收益率。
- 新增按基金收益明细接口或复用基金列表投影，已清仓基金保留历史行。
- 前端总览增加收益拆分，基金收益明细用表格展示，不新建复杂图表。

## 风险与回滚

- 历史交易费用或 lot 缺失时明确标记收益不完整，不以零静默补齐。
- 未确认净值时未实现收益保持未知；已实现收益仍可展示。
- 回滚只移除新增 View 和前端展示，不影响交易账本。
