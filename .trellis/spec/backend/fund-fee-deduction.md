# Fund Fee Deduction Layer

> 基金交易手续费扣除层契约(申购费 + 赎回费 + FIFO lot 匹配)。task
> `07-05-fund-fee-deduction` 引入,解决「历史交易流水只记毛额未扣手续费」问题。

---

## Scope / Trigger

- 触发:买入扣申购费、卖出按持有期查赎回费率阶梯 + FIFO lot 匹配
- 强制 code-spec 深度,因同时命中四项强制条件:
  1. 新增 REST 接口 `GET /api/funds/{id}/fee-rates`
  2. 跨层契约(后端算费 → 前端展示手续费列 / 参考费率)
  3. 数据库 schema 变更(V13 建表 + V14 历史数据回填)
  4. 基础设施集成(项目首个 **Jsoup HTML 爬虫**,区别于既有 GraalVM JS / Jackson JSON 解析器)

---

## Signatures

### 数据库表(V13)

```sql
fund_fee            -- 费率缓存(每基金一行,uq_fund_fee_code 软删唯一索引)
  fund_code VARCHAR(255) NOT NULL
  purchase_rate      NUMERIC(19,8)   -- 原始申购费率(0.015 = 1.5%)
  discount_rate      NUMERIC(19,8)   -- 1折后费率(0.0015),买入实际用此
  sales_service_fee  NUMERIC(19,8)   -- 销售服务费(C 类才有)
  redemption_ladder  VARCHAR(2000)   -- JSON:[{"maxDays":7,"rate":0.015},...,{"maxDays":null,"rate":0}]
  fetched_at         TIMESTAMPTZ NOT NULL

fund_lot            -- 买入 lot(每笔确认的 INCREASE/TRANSFER_IN/INVEST 建一行)
  fund_id, acquire_tx_id, acquire_date TIMESTAMPTZ
  acquire_shares, remaining_shares, acquire_cost_per_share NUMERIC(19,8)
  -- 索引 idx_fund_lot_fund_date(fund_id, acquire_date) WHERE deleted_date IS NULL

fund_lot_redemption -- 卖出消耗 lot 记录(一笔卖出可拆多行,每行对应一个被消耗的 lot)
  lot_id, sell_tx_id, shares_consumed NUMERIC(19,8)
  holding_days INT, redemption_rate NUMERIC(19,8)

fund_transaction   -- 加列(可空)
  ADD COLUMN fee        NUMERIC(19,8)  -- 本次手续费金额
  ADD COLUMN fee_rate   NUMERIC(19,8)  -- 加权费率(fee / gross,小数)
```

### 后端 REST 接口(`FundController`)

```
GET /api/funds/{id}/fee-rates → ApiResponse<FundFeeView>
  FundFeeView(purchaseRate, discountRate, salesServiceFee, redemptionLadder)
```

### 服务/辅助类

```java
// FundFeeService — 费率爬取与缓存
FundFeeSnapshot fetchAndSave(String fundCode);   // 爬 jjfl HTML → 解析 → upsert fund_fee
FundFeeSnapshot getFee(String fundCode);         // 读 DB,缺失返回 FundFeeSnapshot.empty()
FundFeeSnapshot getFeeByFundId(Long fundId);     // 经 fund → fundCode → getFee
FundFeeView      getFeeView(Long fundId);        // 给前端
void             refreshHoldingFunds();          // 遍历 HOLDING 基金刷新

// TransactionConfirmSupport — 买卖确认核心算费(被 NavConfirmService / TransactionConfirmService 共用)
void onBuyConfirmed (FundTransactionEntity tx, BigDecimal navValue);
void onSellConfirmed(FundTransactionEntity tx, BigDecimal navValue);
void onAdjustConfirmed(FundTransactionEntity tx); // ADJUST_OUT 仅缩减 open lot,不算费/不记赎回明细

// FundFeeHtmlParser — Jsoup 静态解析器
static PurchaseFeeRate        parsePurchaseRate(String html);     // → (originalRate, discountRate)
static List<RedemptionTier>   parseRedemptionLadder(String html); // → [(maxDays, rate)...]
static BigDecimal             parseSalesServiceFee(String html);
record PurchaseFeeRate(BigDecimal originalRate, BigDecimal discountRate) {}
record RedemptionTier(Integer maxDays, BigDecimal rate) {}  // maxDays=null → 末档(≥上一档)

// FundFeeRefreshJob
@Scheduled(cron = "0 30 2 * * *", zone = "Asia/Shanghai")  // 北京时间 02:30,早于 03:00 交易确认
```

---

## Contracts

### 买入确认算费公式(`onBuyConfirmed`)

```
discountRate = fee.discountRate() != null ? fee.discountRate() : ZERO
feeAmount    = tx.amount × discountRate
netAmount    = tx.amount − feeAmount
shares       = netAmount ÷ navValue        // 用扣费后净额除净值,不是毛额!
tx.shares    = shares
tx.fee       = feeAmount
tx.feeRate   = discountRate.signum() > 0 ? discountRate : null
new FundLotEntity(acquireDate = tx.tradeDate, acquireShares = shares,
                  remainingShares = shares, acquireCostPerShare = tx.amount / shares)
updateCostPerShare(tx, tx.amount)   // 申购费属于用户实际投入成本
```

### 卖出确认 FIFO 公式(`onSellConfirmed`)

```
lots = findOpenLotsByFundIdOrderByAcquireDateAsc(fundId)   -- 按 acquire_date 升序
if lots.isEmpty():
    -- 降级:无 lot(历史未回填/测试)→ 不扣赎回费,amount = shares × nav,记 warn 不阻断
    amount = shares × nav; fee = 0; feeRate = null; return

remaining = tx.shares
for lot in lots (FIFO):
    if remaining <= 0: break
    consume      = min(remaining, lot.remainingShares)
    holdingDays  = 北京时间自然日(lot.acquireDate → tx.tradeDate)
    rate         = lookupRedemptionRate(ladder, holdingDays)
    totalFee    += consume × nav × rate
    lot.remainingShares -= consume
    记 FundLotRedemptionEntity(lot, sellTx, consume, holdingDays, rate)
    remaining    -= consume

if remaining > 0:
    untracked = 卖出前事实持仓 - open lot 总份额
    remaining <= untracked → 未跟踪 ADJUST_IN 份额按零赎回费降级
    remaining > untracked  → throw INSUFFICIENT_LOTS

grossAmount = shares × nav
tx.amount   = grossAmount − totalFee
tx.fee      = totalFee
tx.feeRate  = grossAmount > 0 ? totalFee ÷ grossAmount : null
```

### 赎回费率阶梯查表(`lookupRedemptionRate`)

- 遍历 `redemptionLadder`,**第一档**满足 `maxDays == null || holdingDays < maxDays` 即返回 `rate`
- 末档 `maxDays == null` 表示「≥ 上一档下限」(如 `大于等于730天` → 0%)
- 阶梯为空 → 返回 `ZERO`(不扣赎回费,降级)

### 东方财富 jjfl HTML 结构契约(Jsoup 解析依赖)

| 数据 | 定位方式 |
|------|---------|
| 申购费率表 | 找含「申购费率」的 `<h4>`,取 `h4.parent().selectFirst("table")` |
| 赎回费率表 | 找含「赎回费率」的 `<h4>`,同上 |
| 销售服务费 | 找含「运作费用」的 `<h4>`,同上,行首含「销售服务费」 |

| 字段 | 解析规则 |
|------|---------|
| 原始费率 | `<strike>1.50%</strike>` → 1.50% |
| 折扣费率 | `<strike>1.50%</strike>|0.15%` → `|` 后 0.15%(1 折) |
| C 类(无折扣) | 无 `<strike>` 且无 `\|` → 单一费率,`PurchaseFeeRate(single, single)` |
| 持有期上限 | `小于7天` → 7;`大于等于730天` → `null`(末档) |

**陷阱**:A 类有 `<strike>` + `|` 折扣;C 类无折扣直接单一费率。解析器必须两种都支持,
否则 C 类基金会被解析成 `originalRate=null, discountRate=null` → 买入不扣费(错误)。

### 双确认路径共用契约

`NavConfirmService`(净值公布后批量确认 PENDING)与 `TransactionConfirmService`(用户手动确认)
**两条路径都必须**经 `TransactionConfirmSupport` 算费,**不得**在各自 service 内联写
`tx.setShares(amount ÷ nav)` / `tx.setAmount(shares × nav)` —— 那会绕过手续费扣除。

---

## Validation & Error Matrix

| 条件 | 行为 | ErrorCode |
|------|------|-----------|
| `fund_fee` 表无此基金记录 | 降级:`discountRate=0`,买入不扣申购费 | (无,正常返回) |
| `redemption_ladder` 为空 / 解析失败 | 降级:`rate=0`,卖出不扣赎回费 | (无,记 warn) |
| 卖出时 `fund_lot` 无可用记录 | 降级:不扣赎回费,`amount = shares × nav`,记 warn | (无,不阻断) |
| 卖出份额 > 可用 lot 余额总和 | 抛异常,交易不确认 | `INSUFFICIENT_LOTS` |
| 卖出份额中的 lot 缺口有事实 ADJUST_IN 份额支撑 | 缺口部分按零赎回费确认,记 warn | (无,正常返回) |
| jjfl HTML 抓取失败(网络/404) | `fetchAndSave` 返回 null,保留旧 `fund_fee` 记录 | (无,记 warn) |
| `navValue` 为 null/0 | `ArithmeticException`(divide by zero) | (既有约束,非本任务) |

---

## Good / Base / Bad Cases

- **Good**:A 类基金,买入 1000 元,申购费率 1.5% 1 折 = 0.15% → fee=1.5,netAmount=998.5,
  nav=1.5 → shares=665.6667。卖出时按 FIFO 跨 lot 匹配持有期查阶梯。
- **Base**:C 类基金,申购费 0%(无折扣)→ `PurchaseFeeRate(0, 0)`,fee=0,shares=amount÷nav。
  持有 ≥730 天 → 赎回费 0%。
- **Bad**:历史卖出交易无对应 lot(V14 未回填 / 测试场景)→ 降级不扣费,**不抛异常**,
  否则会阻断既有的「卖出 PENDING 用最新净值回填 amount」流程(曾导致 2 个测试失败)。

---

## Tests Required

- `FundFeeHtmlParserTest`(10 用例)
  - A 类 strike+折扣、C 类单一费率、5 档 / 2 档阶梯、`parsePercent` / `parseMaxDays` 边界、空 HTML
  - **断言点**:`parsePurchaseRate` A 类返回 `(0.015, 0.0015)`;C 类返回 `(0, 0)`
- `TransactionConfirmSupportTest`(8 用例,`@MockitoExtension`)
  - **断言点**:
    - 买入扣费:`fee = 1000 × 0.0015 = 1.5`,`shares = 998.5 ÷ 1.5`(用同一 MathContext 算期望值)
    - 卖出单 lot:holdingDays=5 → rate=0.015,`fee = 100 × 1.5 × 0.015`
    - 卖出跨 lot FIFO:150@10天 + 50@100天,两档不同 rate,分别断言 `FundLotRedemptionEntity` 行数=2
    - 无 lot 降级:`fee=0`,不抛异常
    - 卖超:`INSUFFICIENT_LOTS`
    - `lookupRedemptionRate` 各档匹配 + 空阶梯返回 ZERO
- 既有 `NavConfirmAndCancelServiceTest` / `TransactionConfirmServiceTest` 的卖出回填用例
  **必须仍通过**(验证降级路径不破坏既有流程)
- V14 回填后,生产 `fund_lot` 应有 `remaining_shares > 0` 的行(手工抽验)

---

## Wrong vs Correct

### Wrong:在确认 service 内联算 shares/amount

```java
// NavConfirmService.tryConfirm() —— 漏掉手续费
tx.setShares(tx.getAmount().divide(navValue, MATH));
tx.setAmount(tx.getShares().multiply(navValue, MATH));
updateCostPerShare(tx, tx.getAmount());
```

**为何错**:买入用毛额除净值 → shares 偏大(未扣申购费);卖出用毛额作 amount → 漏扣赎回费;
`updateCostPerShare` 用毛额 → 成本均价偏高。两条路径(Nav/手动)各写一遍还会漂移。

### Correct:统一委托 TransactionConfirmSupport

```java
// NavConfirmService.tryConfirm() / TransactionConfirmService.confirmOne()
if (tx.isBuy()) {
    transactionConfirmSupport.onBuyConfirmed(tx, navValue);
} else {
    transactionConfirmSupport.onSellConfirmed(tx, navValue);
}
// updateCostPerShare 已移入 support,用 netAmount 而非 amount
```

---

## Design Decisions

### Jsoup 作为项目首个 HTML 解析器

**背景**:东方财富 jjfl 页面是 HTML(含 `<strike>` / `<table>` / `<h4>`),既有解析器
(GraalVM JS 解析 pingzhongdata.js、Jackson 解析 JSON)都无法处理 HTML。

**决策**:引入 `org.jsoup:jsoup:1.18.3`,新建 `FundFeeHtmlParser` 静态工具类。
**何时用 Jsoup**:数据源是 HTML 页面(非 JS 字面量、非 JSON)。
**何时仍用 GraalVM JS**:数据源是 `var x = {...}` 形式的 JS 赋值(pingzhongdata.js)。

### 双确认路径共用 TransactionConfirmSupport

**背景**:`NavConfirmService`(净值公布后批量确认 PENDING)与 `TransactionConfirmService`
(用户手动确认)原本各自内联 `tx.setShares(...)` / `tx.setAmount(...)` / `updateCostPerShare(...)`。

**决策**:抽出 `@Component TransactionConfirmSupport`,两条路径都调 `onBuyConfirmed` /
`onSellConfirmed`。`updateCostPerShare` 也移入 support(份额用 netAmount，成本分子用完整 amount)。
**禁止**在确认 service 内联写算费逻辑,否则两路径会漂移(本任务前的现状)。

### 历史数据不回追 + V14 FIFO 回填

**背景**:历史已确认的卖出交易不补扣赎回费(无 lot 记录,追溯困难且改变历史报表)。

**决策**:V14 用窗口函数 `SUM(shares) OVER (PARTITION BY fund_id ORDER BY confirm_time, id)`
按 FIFO 假设回填 `fund_lot.remaining_shares`。仅插入 `remaining_shares > 0` 的 lot。
历史卖出的 `fee` / `fee_rate` 保持 null,前端显示「-」。

### 费率缺失降级而非阻断

**背景**:`fund_fee` 未爬取 / HTML 解析失败 / 无 lot 记录时,不应阻断交易确认。

**决策**:`getFee` 返回 `FundFeeSnapshot.empty()`(discountRate=0, ladder=空);
`onBuyConfirmed` / `onSellConfirmed` 用 0 费率继续,记 warn。**不抛异常**。
这与项目既有「外部数据缺失降级」模式一致(参考 `MarketRealtimeCache` 保留旧缓存 + warn)。

### 启动线程不刷新逐基金费率

**背景**:`refreshHoldingFunds()` 逐基金访问外部 HTML,受共享限流。放在 `ApplicationReadyEvent` 会让启动时间随持仓数线性增长。

**决策**:启动时不执行费率爬取；每天北京时间 02:30 定时刷新，必须早于 03:00 `NavConfirmJob`。
若外部源失败或缓存仍缺失,交易确认沿用零费率降级，详情查询可按需抓取。
`FundFeeRefreshJobTest` 必须断言该 Job 没有 `@EventListener(ApplicationReadyEvent.class)` 方法，并校验 cron/zone 时序。
