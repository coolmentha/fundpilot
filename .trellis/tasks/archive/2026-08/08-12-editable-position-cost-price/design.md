# 允许用户修改持仓成本价 - 技术设计

## 边界

- 复用“我的基金”现有编辑弹窗和 `PUT /api/funds/{legacyFundId}`，不新增页面或数据库字段。
- `FundService` 继续编排一次编辑请求；成本修正通过 Accounting 的公开 adapter API 进入 `PortfolioCorrectionCommandHandler`。
- `accounting_position.cost_per_share` 是当前持仓成本事实。历史交易和 `fund_lot.acquire_cost_per_share` 不修改。

## 数据流

```text
FundsPage 编辑弹窗
  -> useSaveFund / PUT /api/funds/{legacyFundId}
  -> FundService 校验用户与 PortfolioFund 映射
  -> Accounting correction API
  -> PortfolioCorrectionCommandHandler
  -> Position.correctCostPerShare
  -> PositionRepository
  -> Insights 重新查询并计算当前总盈亏
```

## 契约

- 编辑表单从 Insights 返回的 `costPerShare` 回填；仅 `holdingShares > 0` 时显示。
- `costPerShare == null` 表示本次基金元数据更新不修改成本；非 null 时必须大于 0。
- Accounting 在持仓锁定后校验：组合基金属于当前用户、仍为有效跟踪状态、Position 状态为 `OPEN`。
- 校验失败复用稳定业务错误：非法值返回 `COST_PER_SHARE_INVALID`；非持仓或越权按现有资源不存在/不可修改边界返回。
- 成功后 React Query 失效 `['funds']` 前缀，使列表与详情读取新的 Position 成本并重算未实现盈亏。

## 一致性与并发

- 沿用本地 Spring 事务；成本修正与同一请求中的基金元数据编辑共同提交或回滚。
- 修正和交易确认使用同一 PortfolioFund 写锁顺序，避免与并发买入确认互相覆盖；Position 的乐观锁保留最后防线。
- 不写 legacy `fund.cost_per_share`。Accounting 自 V40 起独占当前持仓成本，避免恢复双事实源。

## 兼容与回滚

- 请求 DTO 已有可选 `costPerShare`，旧调用方不传时行为不变。
- 不新增依赖、不修改 schema、不迁移存量数据。
- 回滚仅需撤销代码；已由用户修正的成本值属于业务数据，不自动恢复。

## 文档影响

- 更新 `CONTEXT.md` 和 `docs/business/fund-and-position.md`，明确成本修正只影响当前持仓基准，不追溯 lot。
- 不新增 ADR：这是 ADR-0013“存储当前成本单价”的可编辑能力，不形成新的难以逆转架构决策。
