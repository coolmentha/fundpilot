# 新增ADJUST调整交易手动修正份额

## Goal

新增"调整"类交易（ADJUST_IN/ADJUST_OUT），让用户手动修正持仓份额差额。解决用户漏记/多记/账面对不上时，无法手动校正持仓的问题。调整交易录入即 CONFIRMED、不算净值手续费、不建 lot，只改持仓份额。

## Background

### 现状（代码库已确认）

- 持仓完全派生自交易：`FundPositionService.getHoldingShares` = `Σ tx.shares × direction WHERE status=CONFIRMED`（`FundPositionService.java:45-47,95-101`）。FundEntity 无独立 shares 字段。
- `direction`（`FundPositionService.java:104-109`）：INCREASE/TRANSFER_IN/INVEST = +1，DECREASE/TRANSFER_OUT = -1。switch 无 default 分支。
- 交易源枚举 `FundTransactionSource.java` 仅 5 值，无 ADJUST/CORRECTION。
- 无任何编辑已确认交易的接口（前后端均无）。CONFIRMED 是终态，撤单/确认都拒绝已确认交易。
- 确认时产生 4 层副作用（lot/lot_redemption/costPerShare/字段方程），直接改已确认交易会破坏一致性。
- 金额实时算：持仓金额 = 持仓份额 × 当前净值（`FundPositionService.getHoldingAmount:57-58`），不存储。

### 用户决策（已确认）

- 方式：新增 ADJUST 调整交易，不改动已有交易，通过新增一笔调整修正差额。
- 方向：ADJUST_IN（调增，+1）/ ADJUST_OUT（调减，-1）两个源，shares 始终正数。
- 流程：录入即 CONFIRMED，不走 PENDING->确认，不算净值/手续费/不建 lot。
- 字段：只调份额（amount/fee/nav 均为 null）。

## Requirements

### R1 新增 ADJUST_IN / ADJUST_OUT 交易源

- `FundTransactionSource` 枚举新增 `ADJUST_IN("调增")`、`ADJUST_OUT("调减")`。
- `FundPositionService.direction`：ADJUST_IN -> +1，ADJUST_OUT -> -1。
- 前端 `constants.js` 的 `fundSourceOptions` 增加"调增""调减"选项；`sourceLabels` 增加 ADJUST_IN/ADJUST_OUT 中文标签。

### R2 录入即 CONFIRMED（不走确认流程）

- `FundTransactionService.createManual` 新增 ADJUST_IN/ADJUST_OUT 分支：
  - 校验 shares 非空正数。
  - 直接创建 `status=CONFIRMED`（非 PENDING），`nav=null, amount=null, fee=null, feeRate=null, confirmTime=now`。
  - **不建 lot、不算手续费、不动 costPerShare**（调整是对账修正，不参与 FIFO 成本体系）。
  - 不设 `relatedFundTransactionEntity`（非转换）。
- `ManualTransactionRequest` 不改（复用 source + shares 字段，targetFundId 为 null）。

### R3 确认/批量确认跳过 ADJUST

- `TransactionConfirmService.confirmOne` 和 `NavConfirmService.tryConfirm` 的 switch：ADJUST_IN/ADJUST_OUT 不走 onBuy/onSell（已 CONFIRMED，不会被确认流程触达，但 switch 需覆盖以防编译失败/防御）。
- ADJUST 交易 status 已是 CONFIRMED，确认接口对其抛 `TRANSACTION_ALREADY_CONFIRMED`（现有逻辑天然生效）。

### R4 前端录入入口

- `FundTransactionTab.jsx`：选 ADJUST_IN/ADJUST_OUT 时，渲染"份额"输入（同卖出类，SELL_SOURCES 扩展或新增判断）。
- 提交时 body 带 `source + shares`，与现有卖出类一致。
- ADJUST 不显示"净值确认后回填"提示（录入即生效）。

## Acceptance Criteria

- [ ] AC1 录入一笔 ADJUST_IN shares=100，交易立即 CONFIRMED，`getHoldingShares` 增加 100。
- [ ] AC2 录入一笔 ADJUST_OUT shares=50，交易立即 CONFIRMED，`getHoldingShares` 减少 50。
- [ ] AC3 ADJUST 交易 `amount/fee/feeRate/nav` 均为 null；不产生 `FundLotEntity`、`FundLotRedemptionEntity`。
- [ ] AC4 ADJUST 交易不影响 `FundEntity.costPerShare`（不重算成本）。
- [ ] AC5 对 ADJUST 交易调 confirm/cancel 接口：confirm 抛 `TRANSACTION_ALREADY_CONFIRMED`；cancel 抛 `TRANSACTION_ALREADY_CONFIRMED`（已确认不可撤）。
- [ ] AC6 持仓金额 = 调整后份额 × 当前净值（实时算，正确反映）。
- [ ] AC7 前端手动录入弹窗可选"调增""调减"，填份额提交后交易列表立即显示该 CONFIRMED 记录。

## Out of Scope

- 调整金额/手续费（用户决策：只调份额；金额实时算）。
- 调整已确认交易字段 + 重算 lot/cost（用户决策：用新增 ADJUST 而非改原交易）。
- ADJUST 交易的 lot 管理（调整不建 lot，不参与 FIFO；若后续卖出时份额池对不上，由系统现有"无 lot 降级不扣赎回费"兜底）。
- 撤回已确认交易（CONFIRMED 仍不可撤）。
- costPerShare 重算（调整不影响成本基准；若用户想修正成本，另行处理）。

## Open Questions

- 无（方式、方向、流程、字段均已确认）。
