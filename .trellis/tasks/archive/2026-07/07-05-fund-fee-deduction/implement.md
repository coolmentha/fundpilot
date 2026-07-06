# Implement — 基金交易手续费扣除

## 实现阶段(有序 checklist)

### 阶段 1:数据模型 + 实体 + Repository

- [ ] 1.1 `backend/pom.xml` 加 `org.jsoup:jsoup:1.18.3` 依赖
- [ ] 1.2 `backend/src/main/resources/db/migration/V13__add_fund_fee_lot.sql`:
  - CREATE TABLE fund_fee(fund_code, purchase_rate, discount_rate, sales_service_fee, redemption_ladder, fetched_at, audit) + uq_fund_fee_code
  - CREATE TABLE fund_lot(fund_id, acquire_tx_id, acquire_date, acquire_shares, remaining_shares, acquire_cost_per_share, audit) + idx_fund_lot_fund_date
  - CREATE TABLE fund_lot_redemption(lot_id, sell_tx_id, shares_consumed, holding_days, redemption_rate, audit) + idx_flr_lot + idx_flr_sell
  - ALTER TABLE fund_transaction ADD COLUMN fee NUMERIC(19,8); ADD COLUMN fee_rate NUMERIC(19,8);
- [ ] 1.3 `FundFeeEntity.java`(@Table(name="fund_fee"), @SQLDelete, extends AbstractEntity, fields 对应列)
- [ ] 1.4 `FundLotEntity.java`(@Table(name="fund_lot"), @ManyToOne(LAZY) fundEntity + acquireTx)
- [ ] 1.5 `FundLotRedemptionEntity.java`(@Table(name="fund_lot_redemption"), @ManyToOne(LAZY) lot + sellTx)
- [ ] 1.6 `FundTransactionEntity.java` 加 `fee`、`feeRate` 字段(BigDecimal)
- [ ] 1.7 `FundFeeRepository.java`(findByFundCode、existsByFundCode)
- [ ] 1.8 `FundLotRepository.java`(@Query findOpenLotsByFundIdOrderByAcquireDateAsc:fund_id, remaining_shares > 0, order by acquire_date ASC)
- [ ] 1.9 `FundLotRedemptionRepository.java`(findBySellTxId、findByLotId)
- [ ] 1.10 验证:`./mvnw test -Dtest= contextLoads` 或启动看 Hibernate validate 通过

### 阶段 2:费率爬取

- [ ] 2.1 `FundFeeSnapshot.java`(record:discountRate, redemptionLadder List<RedemptionTier>, salesServiceFee)
- [ ] 2.2 `RedemptionTier.java`(record:maxDays Integer nullable, rate BigDecimal)
- [ ] 2.3 `EastmoneyFundFeeClient.java`(Feign 接口,@RequestLine("GET /jjfl_{code}.html"))
- [ ] 2.4 `EastmoneyClientConfig.java` 加 EastmoneyFundFeeClient bean(base URL https://fundf10.eastmoney.com,共享 RateLimiter + UA)
- [ ] 2.5 `FundFeeHtmlParser.java`(static 方法,Jsoup):
  - parsePurchaseRate(html) → (original, discount)。定位「申购费率」h4 后的 table,读首档「原费率」(strike)和优惠列
  - parseRedemptionLadder(html) → List<RedemptionTier>。定位「赎回费率」table,逐行解析期限 + 费率
  - parseSalesServiceFee(html) → BigDecimal。定位「运作费用」table「销售服务费率」行
- [ ] 2.6 `FundFeeService.java`:
  - fetchAndSave(fundCode):client.fetchFeeHtml → parser → upsert fund_fee。try/catch 返 null
  - getFee(fundCode):查 fund_fee → FundFeeSnapshot。缺失返 null
  - refreshHoldingFunds():遍历 HOLDING 基金 fetchAndSave
- [ ] 2.7 `FundFeeRefreshJob.java`:@Scheduled(cron="0 30 6 * * *") + @EventListener(ApplicationReadyEvent) 预热
- [ ] 2.8 `FundFeeHtmlParserTest.java`:用真实 jjfl HTML 样本(001071 + 005919 C类)断言解析结果
- [ ] 2.9 验证:`./mvnw test -Dtest=FundFeeHtmlParserTest` + curl 本地爬一只基金

### 阶段 3:历史数据回填

- [ ] 3.1 `V14__backfill_fund_lot.java`(extends BaseJavaMigration):按 design.md 的 FIFO 假设算法回填 fund_lot(只回填剩余未消耗的 lot)
- [ ] 3.2 验证:本地启动看 V14 执行成功 + SELECT fund_lot 有数据 + remaining_shares 合理

### 阶段 4:TransactionConfirmSupport + 申购费扣除

- [ ] 4.1 `TransactionConfirmSupport.java`(@Component):
  - onBuyConfirmed(tx, nav, FundFeeSnapshot):算 fee/netAmount/shares,设 tx 字段,创建 FundLotEntity,更新 FundEntity.costPerShare
  - 注入 FundFeeService、FundLotRepository、FundLotRedemptionRepository
- [ ] 4.2 `NavConfirmService.java` 改造:买入分支调 transactionConfirmSupport.onBuyConfirmed(tx, nav, fundFeeService.getFee(fundCode))。删除原 shares 回填 + updateCostPerShare
- [ ] 4.3 `TransactionConfirmService.java` 改造:同样调 support
- [ ] 4.4 `ErrorCode` 加 INSUFFICIENT_LOTS(卖超时)
- [ ] 4.5 验证:`./mvnw test` 现有 NavConfirmServiceTest 仍绿(可能需调整断言:买入 shares 变少因扣费)

### 阶段 5:赎回费 + FIFO 匹配

- [ ] 5.1 `TransactionConfirmSupport.onSellConfirmed(tx, nav, FundFeeSnapshot)`:FIFO 遍历 lot,算 holdingDays + rate + fee,记 FundLotRedemptionEntity,设 tx.amount/fee/feeRate
- [ ] 5.2 `NavConfirmService` / `TransactionConfirmService` 卖出分支调 onSellConfirmed
- [ ] 5.3 `TransactionConfirmSupportTest.java`:FIFO 单 lot + 跨多 lot + 持有期分档 + 费率缺失降级 + 卖超抛异常
- [ ] 5.4 验证:`./mvnw test -Dtest=TransactionConfirmSupportTest`

### 阶段 6:View + Controller

- [ ] 6.1 `FundTransactionView.java` 加 fee、feeRate 字段 + from() 映射
- [ ] 6.2 `FundFeeView.java`(record:fundCode, purchaseRate, discountRate, salesServiceFee, redemptionLadder List)
- [ ] 6.3 `FundController.java` 加 `GET /api/funds/{id}/fee-rates` → FundFeeView(查 fund.fundCode → fundFeeService.getFee → FundFeeView)
- [ ] 6.4 验证:curl GET /api/funds/{id}/fee-rates 返 JSON

### 阶段 7:前端

- [ ] 7.1 `frontend/src/api/hooks.js` 加 useFundFeeRates(fundId):queryKey ['fund-fee-rates', fundId],queryFn get(/api/funds/${fundId}/fee-rates),enabled !!fundId
- [ ] 7.2 `frontend/src/pages/FundTransactionTab.jsx` 流水表加「手续费」列(title:'手续费', dataIndex:'fee', render: v => v == null ? '-' : money(v))
- [ ] 7.3 `frontend/src/pages/FundDetailPage.jsx` Descriptions 加「参考费率」Item(展示 discountRate% + 赎回阶梯简表)
- [ ] 7.4 验证:`cd frontend && npm run build` 通过

### 阶段 8:收尾

- [ ] 8.1 `./mvnw test` 全绿
- [ ] 8.2 `cd frontend && npm run build` 全绿
- [ ] 8.3 trellis-check(sub-agent 审查代码 vs spec/prd/design)
- [ ] 8.4 trellis-update-spec(新增 lot/FIFO/费率爬取模式是否值得写入 .trellis/spec/)
- [ ] 8.5 git commit(batched,按 trellis Phase 3.4 流程)
- [ ] 8.6 PR + tag + 部署 + 生产验证

## 验证命令汇总

- 后端测试:`./mvnw test`(Windows: `./mvnw.cmd test`)
- 单测定位:`./mvnw test -Dtest=FundFeeHtmlParserTest,TransactionConfirmSupportTest`
- 前端构建:`cd frontend && npm run build`
- 费率爬取本地验证:`curl -s "http://localhost:8080/api/funds/<id>/fee-rates" | head`
- Flyway 验证:启动日志看 `V13`、`V14` 执行成功

## 风险文件 / 回滚点

- `NavConfirmService.java` / `TransactionConfirmService.java`:核心交易逻辑,改动影响所有交易确认。回滚点:阶段 4 提交后若测试红,git revert 该 commit。
- `V14__backfill_fund_lot.java`:历史数据回填,跑一次不可逆(除非删 fund_lot 重跑)。风险:回填算法错导致 lot 余额乱。缓解:先在本地 dry-run(SELECT 验证),生产部署前备份 db-data 卷。
- 费率爬取:依赖天天基金页面结构,若改版会失败。缓解:降级为 fee=0,不阻断交易。

## task.py start 前检查

- [ ] prd.md 验收标准可测
- [ ] design.md 架构决策清晰
- [ ] implement.md checklist 完整
- [ ] implement.jsonl + check.jsonl 各有至少一条真实条目
