# 基金转换转出选转入基金自动计算

## Goal

在转出操作侧支持选择转入基金，将"基金转换"实现为一次联动操作（转出基金 A + 转入基金 B 两条互指交易），并自动计算转入份额与各项费用。解决当前转入/转出作为两条独立记录、互不关联、转出无法选转入基金的问题。

## Background

### 现状（代码库已确认）

- 数据模型已有 `fund_transaction.related_fund_transaction_id` 互指列（`V1__init_schema.sql:173`，外键 `fk_ft_related`）。
- 确认/取消联动已实现：`TransactionConfirmService.confirm:70-73`、`TransactionCancelService.cancel:53-56` 在一侧确认/取消后级联处理另一侧 PENDING 交易。
- **但创建路径从不设置 `relatedFundTransactionEntity`**：`FundTransactionService.createManual:43-78` 仅写入 `(source, amount/shares, status=PENDING, nav=null)`。
- **转出 UI 无选择转入基金的功能**：`FundTransactionTab.jsx:76-97` 中"转出"只是单基金手动录入模态框下拉项之一，仅收集 `shares`；`ManualTransactionRequest` 只有 `(source, amount, shares)`。
- 计算逻辑集中在 `TransactionConfirmSupport`：
  - 买入侧 `onBuyConfirmed:51-78`：`feeAmount = amount × discountRate`，`shares = (amount − fee) / nav`，建 `FundLotEntity`（acquireDate=confirmTime）。
  - 卖出侧 `onSellConfirmed:87-148`：FIFO 遍历 lot，按持有天数阶梯查赎回费率，`amount = shares×nav − 赎回费`。
- 费率数据已具备：`fund_fee` 表含 `purchase_rate / discount_rate / sales_service_fee / redemption_ladder`。
- `docs/PRODUCT.md:434` 与 `docs/prd-portfolio-pnl-manual-tx.md:102` 明确预期："转换 = 两条交易（TRANSFER_OUT + TRANSFER_IN）通过 `relatedTransaction` 互指，撤单一起 CANCELLED"，且"自动互指留待将来"——本任务要补的缺口。

### 真实基金转换规则（参考，本期简化采用）

- 转换是一笔操作：转出基金 A 份额 → 转入基金 B 份额。
- 转换费 = 转出赎回费 + 申购补差费；真实补差费按费率差净额倒算。
- **本期按用户决策简化**：转出侧复用 `onSellConfirmed`（FIFO + 赎回费阶梯），转入侧复用 `onBuyConfirmed`（B 的 discountRate 申购费），不实现申购补差费专用公式。
- 输入单位为"转出份额"；T 日净值，T+1 确认（沿用现有 PENDING→夜间确认机制）。

## Requirements

### R1 转出侧 UI 选择转入基金

- 在 `FundTransactionTab.jsx` 手动录入模态框中，当 `source = TRANSFER_OUT` 时，新增"转入基金"选择器（从现有基金列表中选 B）。
- 转入基金为可选：选了则进入"转换模式"创建联动两条交易；不选则保持现行纯转出单条记录（兼容跨公司超级转换/独立记录场景）。
- 提交时前端将 `targetFundId` 一并传给后端。

### R2 后端创建联动两条交易

- `ManualTransactionRequest` 增加 `targetFundId`（可空）字段。
- `FundTransactionService.createManual` 在 `source=TRANSFER_OUT` 且 `targetFundId` 非空时：
  - 创建转出交易（A，TRANSFER_OUT，填 shares，amount=null，PENDING）。
  - 创建转入交易（B，TRANSFER_IN，shares=null, amount=null，PENDING；amount 待确认时由转出净金额回填）。
  - 两条交易互指 `relatedFundTransactionEntity`（双向 set）。
- 不强制校验 A/B 同基金管理公司（用户自行判断，跨公司可走不选转入基金的纯转出路径）。

### R3 转换份额/费用计算（复用现有买/卖逻辑，顺序依赖）

- 确认时（手动 confirm 或夜间 `NavConfirmService` 批量）对联动两条交易按顺序计算：
  1. 先确认转出腿（A，TRANSFER_OUT）：复用 `onSellConfirmed`，得 `转出净金额 = shares×navA − 赎回费`，写入 A 的 `amount/fee/feeRate/nav`。
  2. 将 A 的 `转出净金额` 作为 B 的 `amount` 回填到转入交易。
  3. 再确认转入腿（B，TRANSFER_IN）：复用 `onBuyConfirmed`，`fee = amount × B.discountRate`，`shares = (amount − fee) / navB`，建 B 的 `FundLotEntity`。
- 联动确认：转出确认后自动确认转入（已有 `TransactionConfirmService.confirm:70-73` 逻辑，需保证顺序为转出先、转入后）。

### R4 撤单联动

- 撤销转换交易时，两条腿一起 CANCELLED（`TransactionCancelService` 已有级联逻辑，验证互指正确写入后即生效）。

## Acceptance Criteria

- [ ] AC1 在基金 A 详情页"手动录入"选"转出"，出现"转入基金"选择器；选 B 提交后，A 产生 TRANSFER_OUT、B 产生 TRANSFER_IN，两条 `related_fund_transaction_id` 互指。
- [ ] AC2 确认该转换后，A 的转出金额/赎回费与现有 `onSellConfirmed` 规则一致（FIFO + 持有期阶梯）。
- [ ] AC3 确认后 B 的 `amount` = A 的转出净金额（shares×navA − 赎回费）；B 的转入份额 = `(amount − B.申购费) / navB`，申购费 = `amount × B.discountRate`。
- [ ] AC4 B 建立新的 `FundLotEntity`；撤销其中一条交易，另一条同步 CANCELLED。
- [ ] AC5 纯转出（不选转入基金）路径行为不变，历史数据兼容。

## Out of Scope

- 申购补差费真实净额倒算公式（本期复用 B 的 discountRate 申购费，与真实账单可能有偏差，用户已接受）。
- 转入份额的持有期穿透（B 的 lot.acquireDate 取确认日，不延续 A 原 lot 持有期；影响 B 后续赎回费阶梯准确性，留待将来）。
- 跨基金公司"超级转换"专用路径（用纯转出 + 纯转入两条独立记录覆盖）。
- 交易日历/15:00 截止校验、节假日 T+N 延迟确认（沿用现有 PENDING→夜间确认机制）。
- 转换费率平台折扣（本期用基金基础 discountRate）。
- 同基金管理公司强制校验。
