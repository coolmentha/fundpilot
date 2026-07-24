# 实施计划：基金转换转出选转入基金自动计算

## 有序清单

### 后端

1. **`ManualTransactionRequest.java`** — 加 `Long targetFundId`（可空）字段，补 record 注释说明转换模式。
2. **`FundTransactionService.createManual`** — `TRANSFER_OUT` 分支内：若 `targetFundId != null`，校验
   `targetFundId != fundId` 且 B 存在，创建 `txOut`(A, TRANSFER_OUT, shares, amount=null) + `txIn`(B, TRANSFER_IN,
   shares=null, amount=null)，双向 `setRelatedFundTransactionEntity`，save 两条，返回 `FundTransactionView.from(txOut)`。
   `targetFundId` 为空走原逻辑。
3. **`TransactionConfirmService.confirm`** — 替换第 66-73 行级联块为配对感知逻辑（见 design.md）：识别 (TRANSFER_OUT,
   TRANSFER_IN) 组合，先 `confirmOne(outLeg)`，回填 `inLeg.setAmount(outLeg.getAmount())`，再 `confirmOne(inLeg)`；非转换沿用原级联。
   `confirmOne` 不改。
4. **`NavConfirmService.tryConfirm`** — 顶部加 `if (tx.getStatus() != PENDING) return false;` 守卫；在
   `DECREASE/TRANSFER_OUT` 确认后（第 102-104 行 onSellConfirmed 之后）加：若 `related` 是 TRANSFER_IN 且 PENDING，
   `related.setAmount(tx.getAmount())` 并递归 `tryConfirm(related, dayStart, dayEnd)`。
5. **单测补充**：
    - `FundTransactionServiceTest`：转换模式创建两条互指记录；targetFundId=A 报错；B 不存在报错；纯转出不变。
    - `TransactionConfirmServiceTest`：转换两腿联动确认，转入 amount=转出净金额、shares/fee 符合 onBuy/onSell；确认
      in-leg（先点转入腿）也能正确顺序确认。
    - `NavConfirmServiceTest`：A/B 当日净值均有时批量确认两腿；仅 A 有净值时转出确认、转入留 PENDING (amount 已回填)
      ；同批内转入腿先到不报错。
    - 撤单级联：`TransactionCancelServiceTest` 已有互指场景，跑通即可。

### 前端

6. **`FundTransactionTab.jsx`** — `source===TRANSFER_OUT` 时渲染"转入基金"Select（`useFunds()` 数据，排除当前
   fundId，option label 用基金名称）。`submit()`：`isSell && values.targetFundId` 时 body 带 `targetFundId`。选择器可选（留空=纯转出）。
   `fundSourceOptions` 不改。
7. **`hooks.js` useCreateManualTransaction** — `mutationFn` 已透传 body（含 targetFundId，无需改）；`onSuccess` 失效查询加
   `qc.invalidateQueries({queryKey: ['funds']})`（刷新 B 的持仓摘要）。

## 验证命令

- 后端：`cd backend && ./mvnw test`（全量单测；若需聚焦：
  `./mvnw test -Dtest=FundTransactionServiceTest,TransactionConfirmServiceTest,NavConfirmServiceTest,TransactionCancelServiceTest`）
- 前端：`cd frontend && npm run build`（vite 构建捕获 JSX/语法错误）
- 全量：先 backend test，再 frontend build

## 高风险文件 / 回滚点

| 文件                                  | 风险                                    | 回滚                                                 |
|---------------------------------------|-----------------------------------------|------------------------------------------------------|
| `TransactionConfirmService.confirm`   | 配对顺序写错 → 转入腿 amount 未回填报错 | 单测覆盖先转出/先转入两路径                          |
| `NavConfirmService.tryConfirm`        | 递归 + 守卫写错 → 死循环或重复确认      | 守卫 `status!=PENDING` 必须在最前；单测覆盖乱序      |
| `FundTransactionService.createManual` | 双向 set 遗漏 → 级联失效                | 单测断言两端 relatedFundTransactionEntity 非空且互指 |

## 实施前检查

- [ ] 确认 `ErrorCode` 已有 `FUND_NOT_FOUND`、`MANUAL_TRANSACTION_FIELD_REQUIRED`（探查确认存在，复用）。
- [ ] 确认 `useFunds()` 返回结构含基金名称字段用于 option label（探查见 hooks.js:12，需对齐 FundView 字段）。
- [ ] 实施前运行 `trellis-before-dev` 注入 spec 约定。
