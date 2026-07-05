# Design — 基金交易手续费扣除

## 架构概览

新增 4 个后端组件,改造 2 个确认服务,前端 3 处展示。

组件交互:
- EastmoneyFundFeeClient (Feign) GET /jjfl_{code}.html → FundFeeHtmlParser (Jsoup) → FundFeeService → fund_fee 表
- FundFeeRefreshJob 每日 06:30 + ApplicationReadyEvent 预热 → 调 FundFeeService 刷新持仓基金
- NavConfirmService / TransactionConfirmService → TransactionConfirmSupport(新共享 helper)→ FundFeeService 取费率 + FundLotRepository 建/消耗 lot
- FundController GET /fee-rates → FundFeeService 返 FundFeeView
- 前端 useFundFeeRates → /fee-rates;FundTransactionTab 加手续费列;FundDetailPage 加参考费率

## 数据模型(V13 migration)

### fund_fee(费率缓存)
列:fund_code VARCHAR(255) NOT NULL、purchase_rate NUMERIC(19,8)(原)、discount_rate NUMERIC(19,8)(优惠)、sales_service_fee NUMERIC(19,8)(C类年化)、redemption_ladder VARCHAR(2000)(阶梯 JSON)、fetched_at TIMESTAMPTZ、audit。
唯一索引:uq_fund_fee_code ON (fund_code) WHERE deleted_date IS NULL。

赎回阶梯 JSON 格式:[{"maxDays":7,"rate":0.015},{"maxDays":30,"rate":0.0075},...,{"maxDays":null,"rate":0}]。maxDays=null 表示"≥上一档上限"。

### fund_lot(买入 lot)
列:fund_id BIGINT NOT NULL、acquire_tx_id BIGINT NOT NULL(买入 tx id)、acquire_date TIMESTAMPTZ NOT NULL(= 买入 tx confirmTime)、acquire_shares NUMERIC(19,8) NOT NULL、remaining_shares NUMERIC(19,8) NOT NULL、acquire_cost_per_share NUMERIC(19,8) NOT NULL、audit。
索引:idx_fund_lot_fund_date ON (fund_id, acquire_date) WHERE deleted_date IS NULL(供 FIFO 升序遍历)。
外键:fund_id → fund.id;acquire_tx_id → fund_transaction.id(均 ON DELETE CASCADE 软删)。

### fund_lot_redemption(卖出消耗 lot)
列:lot_id BIGINT NOT NULL、sell_tx_id BIGINT NOT NULL、shares_consumed NUMERIC(19,8) NOT NULL、holding_days INT NOT NULL、redemption_rate NUMERIC(19,8) NOT NULL、audit。
索引:idx_flr_lot ON (lot_id)、idx_flr_sell ON (sell_tx_id)。

### fund_transaction 加列
ALTER TABLE fund_transaction ADD COLUMN fee NUMERIC(19,8)(本次交易手续费);ADD COLUMN fee_rate NUMERIC(19,8)(加权费率 = fee / 金额)。均可空(历史数据 null)。

## 费率爬取数据流

1. EastmoneyFundFeeClient(Feign 接口,base URL https://fundf10.eastmoney.com):@RequestLine("GET /jjfl_{code}.html") String fetchFeeHtml(@Param("code") String code)。
2. FundFeeHtmlParser(static 方法,Jsoup 解析):
   - parsePurchaseRate(html) → (原费率, 优惠费率)。定位「申购费率」表(th.text 含 "申购费率"),读第一行的「原费率」(strike 标签)和「天天基金优惠费率」列。多档按金额(<100万)取首档。
   - parseRedemptionLadder(html) → List<{maxDays, rate}>。定位「赎回费率」表,逐行解析「适用期限」(如 "持有期限<7天" → maxDays=7)和「赎回费率」。"≥730天" → maxDays=null。
   - parseSalesServiceFee(html) → BigDecimal。定位「运作费用」表「销售服务费率」行。
3. FundFeeService:
   - fetchAndSave(fundCode):调 client + parser,upsert fund_fee 表。失败返 null,不抛。
   - getFee(fundCode):查 fund_fee 表,返 FundFeeSnapshot(discountRate, redemptionLadder, salesServiceFee)或 null。
   - refreshHoldingFunds():遍历 FundRepository.findByStatus(HOLDING),逐个 fetchAndSave。受 RateLimiter 2 req/s 限流。
4. FundFeeRefreshJob:@Scheduled(cron = "0 30 6 * * *") 每日 06:30 + @EventListener(ApplicationReadyEvent) 启动预热。try/catch 不阻断。

## 买入扣费数据流(TransactionConfirmSupport.onBuyConfirmed)

输入:FundTransactionEntity tx(已设 amount)、BigDecimal nav、FundFeeSnapshot fee(可 null)。
1. discountRate = fee == null ? ZERO : fee.discountRate()。
2. feeAmount = tx.amount × discountRate;netAmount = tx.amount − feeAmount;shares = netAmount / nav(MATH DECIMAL64)。
3. tx.shares = shares;tx.nav = nav;tx.fee = feeAmount;tx.feeRate = discountRate。
4. 创建 FundLotEntity(fundId, acquireTxId=tx.id, acquireDate=tx.confirmTime, acquireShares=shares, remainingShares=shares, acquireCostPerShare=netAmount/shares)。
5. 更新 FundEntity.costPerShare(加权均价,用 netAmount 替代原 amount):numerator = oldCost × oldShares + netAmount;newCost = numerator / (oldShares + shares)。

## 卖出 FIFO 数据流(TransactionConfirmSupport.onSellConfirmed)

输入:FundTransactionEntity tx(已设 shares)、BigDecimal nav、FundFeeSnapshot fee(可 null)。
1. ladder = fee == null ? List.of() : fee.redemptionLadder()。
2. remaining = tx.shares;feeAmount = ZERO;redemptions = []。
3. lots = lotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(fundId)(remaining_shares > 0)。
4. for lot in lots:
   - if remaining ≤ 0:break。
   - consume = min(lot.remainingShares, remaining)。
   - holdingDays = Days.between(lot.acquireDate, tx.confirmTime)(自然日)。
   - rate = lookupRate(ladder, holdingDays)(ladder 空则 ZERO)。
   - feeAmount += consume × nav × rate。
   - lot.remainingShares -= consume;save(lot)。
   - redemptions.add(new FundLotRedemptionEntity(lot.id, tx.id, consume, holdingDays, rate))。
   - remaining -= consume。
5. if remaining > 0:抛 ErrorCode.INSUFFICIENT_LOTS(卖超)。
6. tx.amount = tx.shares × nav − feeAmount;tx.nav = nav;tx.fee = feeAmount;tx.feeRate = feeAmount / (tx.shares × nav)。
7. 批量 save(redemptions)。

lookupRate(ladder, holdingDays):遍历 ladder,首个 maxDays==null 或 holdingDays < maxDays 的 tier.rate 即返回;都未命中返最后一档(或 ZERO)。

## 确认路径统一

抽 TransactionConfirmSupport(@Component),暴露 onBuyConfirmed(tx, nav, fee) / onSellConfirmed(tx, nav, fee)。
NavConfirmService.confirmPendingTransactions:遍历 PENDING,按 source 分发到 support。删除原 updateCostPerShare 逻辑(移入 support)。
TransactionConfirmService.confirm(单笔):同样调 support。
两边不再各写一份扣费/lot/成本逻辑,避免 divergence。

## 历史数据迁移(V13 内)

fund_transaction.fee / fee_rate 列可空,历史行保持 null(前端显示 '-')。

fund_lot 回填:用 Flyway Java migration(org.flywaydb.core.api.migration.BaseJavaMigration),实现「FIFO 假设历史卖出已消耗最早 lot」,只回填剩余未消耗的 lot:
```
for each fund (CONFIRMED holding):
  buys = findByFundIdAndSourceInAndStatusOrderByConfirmTimeAsc(fundId, [INCREASE,TRANSFER_IN,INVEST], CONFIRMED)
  sells = sum(shares) of DECREASE/TRANSFER_OUT CONFIRMED
  remaining = sells
  for buy in buys:
    if remaining >= buy.shares:
      remaining -= buy.shares  # 整个 lot 被历史卖出消耗,跳过
    else:
      lot.remainingShares = buy.shares - remaining
      lot.acquireCostPerShare = buy.amount / buy.shares
      save(lot)
      remaining = 0
```
历史卖出不补记 fund_lot_redemption(无 lot 锚定,且 R6.2 声明不回溯)。
FundEntity.costPerShare 不重算(R6.3)。

## 兼容性 & 权衡

- Jsoup vs 正则:选 Jsoup。jjfl 页面 HTML 表格结构复杂(多表、strike 标签、| 分隔),正则脆弱。pom.xml 加 org.jsoup:jsoup:1.18.3。
- lot 回填策略:FIFO 假设(最早卖出消耗最早 lot)是行业惯例,与未来新卖出 FIFO 一致。误差仅影响"哪几个 lot 被消耗"的归属,不影响总持仓。历史 costPerShare 不重算,避免历史盈亏跳变。
- 费率缺失降级:fee=0 + warn,不阻断。比硬抛异常好(爬取失败不应卡住交易确认)。
- discount_rate 口径:天天基金优惠(1折)。不支持银行柜台不打折场景(Out of Scope)。
- MATH 沿用 DECIMAL64(与现有 NavConfirmService 一致,避免引入新 MathContext 造成不一致)。
- 前端独立 endpoint /fee-rates:阶梯表是 List 结构,嵌入 FundView 会拖累现有 GET /funds/{id}。独立 endpoint + useFundFeeRates 更干净。

## 回滚考虑

- V13 migration:如出错,docker compose 层面回滚到上一镜像(新表/新列不影响旧代码读旧表)。
- 代码回滚:fee/fee_rate 列可空,旧代码不读这两列,无影响。fund_lot/fund_lot_redemption 表旧代码不用,无影响。
- 费率爬取失败:降级为毛额记账,等同回滚到当前行为。
