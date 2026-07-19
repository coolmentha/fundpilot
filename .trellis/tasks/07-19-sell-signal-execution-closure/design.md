# v0.8 技术设计

## 数据契约

- `SignalLogView` 增加可选 `relatedTransactionId`、`relatedTransactionStatus`，由现有 `signal_log_id` 反查交易。
- `FundTransactionView` 增加可选 `signalReason`，让操作确认页不依赖再次请求信号详情。
- 不新增表；撤销状态从现有交易状态实时投影。

## 页面流转

`SignalsPage 采纳 → /confirm?signalId=id → 定位待确认交易`

`SignalsPage 查看交易 → /funds/{fundId}?transactionId=id`

- 操作确认页读取 `signalId`，找到对应交易并滚动/高亮。
- 采纳成功后的跳转由前端路由完成；后端仍负责防重复和状态校验。
- 交易被撤销时，信号状态保持 RESPONDED，展示撤销后的交易状态。

## 兼容性

- 新字段均可为空，旧信号和历史无关联交易时不显示入口。
- 复用现有 `SignalQueryService`、`FundTransactionRepository` 和 `signalLogId` 索引。
- 不改变 SELL 生成、确认、撤销和信号状态机。
