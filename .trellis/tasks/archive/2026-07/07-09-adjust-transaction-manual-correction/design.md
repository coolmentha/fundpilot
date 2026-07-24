# 设计+实施：新增ADJUST调整交易手动修正份额

## 设计

### 数据流

```
前端 FundTransactionTab(source=ADJUST_IN, shares=100)
  -> POST /api/funds/{id}/transactions {source, shares}
  -> FundTransactionService.createManual:
      ADJUST 分支:校验 shares 正数
      tx = FundTransactionEntity(fund, source, shares, status=CONFIRMED,
                                 nav=null, amount=null, fee=null, confirmTime=now)
      save(tx)  // 不建 lot,不算费,不动 costPerShare
      return view
  -> 持仓立即变:getHoldingShares = Σ shares×direction,ADJUST_IN=+1
```

### 关键决策

- **录入即 CONFIRMED**：调整是对账修正，不需等净值。amount/fee/nav 均空，金额实时算（份额×当前净值）。
- **不建 lot**：调整不参与 FIFO 成本体系。后续卖出若份额超过 lot 池，走现有"无 lot 降级不扣赎回费"兜底（
  `TransactionConfirmSupport.java:98-107`）。已知偏差：调整增的份额卖出时不扣赎回费（与建仓 lot 不同），可接受（对账修正场景）。
- **不动 costPerShare**：调整只改份额，不影响成本基准。若用户想修正成本，另行处理。

### 影响面（switch 覆盖）

新增 ADJUST_IN/ADJUST_OUT 枚举值后，以下 switch 需覆盖（否则编译失败/逻辑遗漏）：

| 文件:行                                        | switch           | ADJUST 处理                                             |
|------------------------------------------------|------------------|---------------------------------------------------------|
| `FundPositionService.direction:105`            | source→direction | ADJUST_IN→+1, ADJUST_OUT→-1                             |
| `FundTransactionService.createManual:55`       | source→校验字段  | ADJUST_IN/OUT→校验 shares 正数,直接 CONFIRMED           |
| `TransactionConfirmService.confirmOne:110,128` | source→校验/算费 | ADJUST 不走 onBuy/onSell（防御，已 CONFIRMED 不会触达） |
| `NavConfirmService.tryConfirm:93,111`          | source→校验/算费 | 同上                                                    |

confirmOne/tryConfirm 的 switch 加 ADJUST_IN,ADJUST_OUT 分支但 **不调 onBuy/onSell**（空分支或记 warn），因为 ADJUST 录入即
CONFIRMED，不会被批量确认/手动确认触达。但 switch 必须覆盖以防 `IllegalStateException`（未覆盖的枚举值）。

### 不动项

- `FundTransactionEntity`、schema、`ManualTransactionRequest`、`TransactionConfirmSupport`、`FundLot*`、
  `FundEntity.costPerShare`。
- 确认/撤单接口逻辑（ADJUST 已 CONFIRMED，天然被 `TRANSACTION_ALREADY_CONFIRMED` 拦截）。

## 实施

1. **`FundTransactionSource.java`** — 加 `ADJUST_IN("调增")`、`ADJUST_OUT("调减")`。
2. **`FundPositionService.direction`** — switch 加 `case ADJUST_IN -> ONE; case ADJUST_OUT -> ONE.negate();`。
3. **`FundTransactionService.createManual`** — switch 加 `case ADJUST_IN, ADJUST_OUT` 分支：校验 shares 非空正数，建
   `status=CONFIRMED` 交易（nav/amount/fee/feeRate=null, confirmTime=now），save 返回。不走转换分支（targetFundId 必为 null）。
4. **`TransactionConfirmService.confirmOne`** — 两个 switch 加 `case ADJUST_IN, ADJUST_OUT` 空分支（不校验
   amount/shares、不调 support，因已 CONFIRMED 不会触达）。
5. **`NavConfirmService.tryConfirm`** — 两个 switch 同上。
6. **前端 `constants.js`** — `fundSourceOptions` 加 `{value:'ADJUST_IN',label:'调增'}`、
   `{value:'ADJUST_OUT',label:'调减'}`；`sourceLabels` 加映射。
7. **前端 `FundTransactionTab.jsx`** — `SELL_SOURCES` 加 ADJUST_OUT（调减也填份额）；或改判断逻辑让 ADJUST_IN/OUT
   都填份额。ADJUST 不显示"净值确认后回填"提示。
8. **测试**：`FundTransactionServiceTest` 加 ADJUST_IN/OUT 录入即 CONFIRMED + 持仓变化用例。

## 验证

- 后端：`cd backend && ./mvnw test -Dtest=FundTransactionServiceTest,FundPositionServiceTest`（需 JDK 25）。
- 前端：`cd frontend && npm run build`。

## 风险

| 风险                                          | 缓解                                                                                 |
|-----------------------------------------------|--------------------------------------------------------------------------------------|
| switch 遗漏 ADJUST 导致 IllegalStateException | 编译期强制覆盖（无 default 的 switch 会报编译错）                                    |
| ADJUST 增份额后卖出 lot 不足                  | 现有"无 lot 降级"兜底，不阻断                                                        |
| ADJUST_OUT 超过持仓导致负份额                 | direction 派生允许负值（已有"超卖"语义，`FundPositionService.java:44` 注释），不阻断 |
