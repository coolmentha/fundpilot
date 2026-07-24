# 设计：基金转换转出选转入基金自动计算

## 架构与边界

无 schema 迁移。`fund_transaction.related_fund_transaction_id` 互指列已存在（`V1__init_schema.sql:173`
），确认/取消级联已实现。本任务只补"创建时建立互指 + 确认时两腿顺序计算 + 前端选转入基金"三个缺口。

改动面：

- 后端：`ManualTransactionRequest`（加字段）、`FundTransactionService.createManual`（转换分支）、
  `TransactionConfirmService.confirm`（两腿顺序+金额回填）、`NavConfirmService.tryConfirm`（守卫+金额回填+即时续确认转入腿）。
- 前端：`FundTransactionTab.jsx`（转入基金选择器）、`hooks.js`（请求体带 targetFundId + 失效查询放宽）。
- 撤单：`TransactionCancelService` 已有级联，不改，仅测试验证。

## 数据流与契约

### 创建（转换模式）

```
前端 FundTransactionTab(source=TRANSFER_OUT, targetFundId=B, shares=N)
  → POST /api/funds/{A}/transactions {source, shares, targetFundId}
  → FundTransactionService.createManual(A, req):
      校验 targetFundId != A、B 存在
      txOut = FundTransactionEntity(A, TRANSFER_OUT, shares=N, amount=null, PENDING)
      txIn  = FundTransactionEntity(B, TRANSFER_IN, shares=null, amount=null, PENDING)
      txOut.setRelatedFundTransactionEntity(txIn)
      txIn.setRelatedFundTransactionEntity(txOut)
      save(txOut); save(txIn)   // 双向互指需显式 set 两端
      return FundTransactionView.from(txOut)   // 返回转出腿(触发腿)
```

转入腿 `amount=null`：确认时由转出净金额回填，不在此阶段计算。

### 确认 — 手动（TransactionConfirmService.confirm）

现状 `confirmOne(tx)` 后级联 `confirmOne(related)`，但转入腿 amount=null 会触发买入类 null 校验报错。改为配对感知：

```
confirm(txId):
  tx = load(txId), 状态校验(沿用)
  related = tx.relatedFundTransactionEntity
  isConversion = related != null && 是 (TRANSFER_OUT, TRANSFER_IN) 组合
  if isConversion:
    outLeg = tx.source==TRANSFER_OUT ? tx : related
    inLeg  = 另一条
    confirmOne(outLeg)              // onSellConfirmed → 设置 outLeg.amount
    if inLeg.status==PENDING:
      inLeg.setAmount(outLeg.getAmount())  // 转出净金额 = 转入本金
      confirmOne(inLeg)            // onBuyConfirmed → 设置 inLeg.shares/fee
  else:
    confirmOne(tx)                 // 普通单腿(沿用)
    if related!=null && related.status==PENDING: confirmOne(related)  // 沿用级联
```

`confirmOne` 不改：它已做 nav 取数 + null 校验 + 调 onBuy/onSell。只需在调 `confirmOne(inLeg)` 前回填 amount，买入类 amount
null 校验即通过。手动路径 nav 缺失会抛 `NAV_HISTORY_EMPTY`，整事务回滚（要求 A/B 净值均就绪，符合手动确认语义）。

### 确认 — 夜间批量（NavConfirmService.tryConfirm）

批量按基金当日净值确认，转换两腿可能乱序、A/B 净值不同日就绪。改造：

1. tryConfirm 顶部加守卫：`if (tx.status != PENDING) return false;`（防止同批内转出腿已连带确认转入腿后，循环再到转入腿时重复确认）。
2. 转出腿确认后（onSellConfirmed 设 amount），若 related 是 TRANSFER_IN 且 PENDING：
    - `related.setAmount(tx.getAmount())` 回填转入本金。
    - 递归 `tryConfirm(related, dayStart, dayEnd)`：若 B 当日净值可得则即时确认转入腿；不可得则转入腿留 PENDING（amount
      已回填），下次 job 自动续。
3. 转入腿先于转出腿被循环到时：amount 仍为 null → 沿用现有 "amount 为空跳过" warn 分支，等转出腿处理时回填并递归确认。

JPA 同事务持久化上下文保证 `tx.getRelatedFundTransactionEntity()` 与 pendings 列表中转入腿是同一受管实例，回填对后续循环可见。

### 撤单（TransactionCancelService.cancel）

不改。`cancel:53-56` 已级联撤 related PENDING 腿。互指由创建分支正确写入后即生效。

## 计算口径（用户决策：简化复用现有买/卖逻辑）

- 转出腿：`onSellConfirmed` — FIFO 消耗 A 的 lot，按持有期阶梯查赎回费率，`amount = shares×navA − 赎回费`。
- 转入腿：`onBuyConfirmed` — `fee = amount × B.discountRate`，`shares = (amount − fee) / navB`，建 B 的 `FundLotEntity`
  （acquireDate=确认日）。
- 转入 `amount` = 转出 `amount`（转出净金额，已扣赎回费）。
- 不实现申购补差费净额倒算；不强制同基金公司；不穿透持有期（B 的 lot 从确认日起算，已知偏差，列入 Out of Scope）。

## 兼容性与回归

- 纯转出（targetFundId 为空）：`createManual` 走原分支，行为完全不变。
- 历史无 related 的转入/转出记录：确认/撤单走非配对分支，不受影响。
- 手动确认返回 `List<FundTransactionView>`、撤单返回 `List`：Controller 已是列表签名，不改。
- 创建接口返回单条 `FundTransactionView`（转出腿）：前端 `submit()` 不用返回值，兼容。

## 风险与回滚

| 风险                                                          | 缓解                                                  |
|---------------------------------------------------------------|-------------------------------------------------------|
| 批量同批内转入腿先到、转出腿后到 → 转入腿当天可能延迟一日确认 | 可接受（追踪场景）；amount 回填后下次 job 自动续      |
| 双向互指 set 遗漏一端 → 级联失效                              | createManual 显式 set 两端；单测覆盖                  |
| 手动确认转入腿时 B 净值缺失 → 整事务回滚（转出腿也不确认）    | 符合手动确认语义；提示用户待净值就绪后重试            |
| JPA 受管实例假设不成立 → 回填不可见                           | 同事务 + 持久化上下文 identity map 保证；集成测试验证 |

回滚点：每层改动独立，可按文件回退。无 schema 变更，无数据迁移风险。
