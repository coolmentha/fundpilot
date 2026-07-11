# Transaction Consistency

## 1. Scope / Trigger

适用于信号回应、手工交易输入、夜间净值确认、基金状态重算、转换两腿状态、ADJUST/初始持仓 FIFO lot 和交易日历同步。任何改动触及 PENDING/CONFIRMED 状态、lot、定时任务或 DATE 存储时，必须按本契约检查。

## 2. Signatures

```java
int NavConfirmService.confirmPendingTransactions(Instant tradeDayUtc);
int NavConfirmService.confirmPendingTransactionsForFund(Long fundId);
int PendingTransactionCompensationService.compensateAll();
int PendingTransactionCompensationService.compensateFund(Long fundId);
List<FundTransactionEntity> TransactionConfirmService.confirm(Long transactionId);
List<FundTransactionEntity> TransactionCancelService.cancel(Long transactionId);
FundTransactionEntity SignalOperationService.confirmOperation(Long fundId, Long signalLogId, ConfirmOperationRequest request);
FundEntity FundPositionService.reconcileStatus(Long fundId);
void TransactionConfirmSupport.onAdjustConfirmed(FundTransactionEntity tx);
void TransactionConfirmSupport.onExistingPositionConfirmed(FundTransactionEntity tx, BigDecimal acquireCostPerShare);
int TradingCalendarRepository.insertTradingDayIfAbsent(Instant calendarDate);
Optional<Instant> TradingCalendarRepository.findMaxCalendarDate();
int TradingCalendarSyncService.sync();
int TradingCalendarSyncService.syncFull();
FundTransactionView FundTransactionService.createManual(Long fundId, ManualTransactionRequest request);
boolean FundTransactionRepository.existsByDcaPlanIdAndTradeDateBetween(Long dcaPlanId, Instant start, Instant end);
```

前端 `POST /api/funds/{fundId}/transactions`：买入类传正数 `amount`，卖出/调整类传正数 `shares`，转换可额外传 `targetFundId`；
可选 `tradeDate: Instant` 表示真实交易发生时间，省略时后端使用当前时间。

数据库 `fund_transaction.trade_date TIMESTAMPTZ` 保存业务发生时间；`created_date` 继续由 Spring 审计维护。V16 用 `created_date` 回填存量行，
并建立 `(fund_id, trade_date DESC) WHERE deleted_date IS NULL` 索引。

## 3. Contracts

- `ChinaTradingDate` 是北京时间自然日到数据库 UTC 00:00 DATE 标签的唯一转换入口。
- `MarketDataFetchService` 写 snapshot、`SignalQueryService.today` 查当日信号也必须使用同一日期标签，手动入口不能按 JVM UTC 截日。
- `NavConfirmJob` 次日 03:00 用 `ChinaTradingDate.previousUtcDate(clock.instant())` 传前一业务自然日标签。
- `tradeDate` 是业务交易发生时间，`createdDate` 只是审计创建时间；所有新建交易路径必须显式写 `tradeDate`。
- `NavConfirmService` 优先按每笔 PENDING 交易的 `tradeDate` 选择净值日；仅存量 `tradeDate` 为空时回退 `createdDate`，Job 参数是最后降级值。
- 手动确认与自动确认都必须按交易 `tradeDate` 对应的北京时间自然日取单位净值；累计净值仅用于复权分析，禁止用于真实交易份额、金额和市值。
- 手动转换的转出、转入两腿必须复用同一 `tradeDate`；流水按 `coalesce(tradeDate, createdDate)` 倒序。
- 净值历史新增行提交后发布 `FundNavUpdatedEvent`，`AFTER_COMMIT` 监听器按基金推进 PENDING 交易，确认失败不得回滚净值。
- 应用启动时和每小时第 5 分钟运行待确认补偿；按基金使用独立事务，单只失败不阻断其他基金。
- `NavConfirmJob`、`DailyNavConfirmJob` 和补偿 Job 的 cron 必须显式声明 `zone = "Asia/Shanghai"`。
- SignalLog 回应必须校验路径基金归属，并在悲观锁内检查未软删关联交易，保证同一信号只生成一笔交易。
- 当日已有信号关联交易时，管理员重跑必须保留原信号并停止覆盖；只有未回应信号允许覆盖重算。
- 生效策略正常评估但未满足卖出条件时返回 `NO_SELL_TRIGGER`，不能返回 `NO_STRATEGY`。
- `MIN_HOLD_DAYS` 只查最近一笔 CONFIRMED 的 INCREASE/TRANSFER_IN/INVEST；卖出、调整和 PENDING 不得重置持有期。
- 创建 PENDING 交易不得提前修改基金状态；确认和撤销后统一调用 `reconcileStatus` 按 CONFIRMED 净份额重算。
- 转换 PENDING/PENDING 仅在两腿同日净值都存在时原子确认：转出 -> 净额回填 -> 转入。
- 历史 CONFIRMED/PENDING 只补确认转入腿，不重复执行转出 FIFO。
- 转换任一腿已确认时，不允许撤销另一腿。
- ADJUST_OUT 按 FIFO 缩减 open lot，不算费、不写赎回明细；ADJUST_IN 不建收费 lot。
- 初始持仓同步确认时必须创建 open lot，`confirmTime` 使用最终 `openedAt`，且不重复扣申购费。
- lot 的 `acquireDate` 和赎回持有期终点使用 `tradeDate`；仅存量空值才回退 `createdDate/confirmTime`。
- V17 以一次性事务重放 CONFIRMED 账本；失败整体回滚并阻止应用带半完成账本启动，完成后不得重复执行。
- 同一定投计划同一北京时间自然日由部分唯一索引最终兜底，Job 使用 `ON CONFLICT DO NOTHING` 原子生成。
- 卖出存在 lot 缺口时，只有卖出前事实持仓中确有未跟踪份额，缺口才按零赎回费降级。
- 交易日历使用数据库 `ON CONFLICT DO NOTHING` 原子插入，不使用“先查后插”实现幂等。
- 日常 `sync()` 空表全量、非空表只写最大日期之后；管理 `syncFull()` 遍历全量以补历史缺口。
- DCA 周计划只接受周一至周五；月计划允许连续休市后跨月顺延到首个交易日。
- DCA 幂等按 `dcaPlanId + tradeDate` 的北京时间自然日范围检查任意状态；CONFIRMED/CANCELLED 都不得重建本期交易。

## 4. Validation & Error Matrix

| 条件 | 行为 | ErrorCode |
|---|---|---|
| 买入 amount 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 卖出/调整 shares 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 手动交易 `tradeDate` 晚于当前时间 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 定投金额非正、频率为空、周计划日不在 1..5、月计划日不在 1..28 | 拒绝创建/更新/激活 | `DCA_PLAN_INVALID` |
| 信号回应实际金额或份额为零/负数 | 拒绝回应 | `SIGNAL_OPERATION_VALUE_INVALID` |
| 转换转入已确认、转出仍待确认 | 拒绝继续确认 | `ILLEGAL_STATE_TRANSITION` |
| 转换关联腿已确认，撤销另一腿 | 拒绝半撤销 | `TRANSACTION_ALREADY_CONFIRMED` |
| lot 缺口大于事实未跟踪份额 | 交易不确认 | `INSUFFICIENT_LOTS` |
| 路径 fundId 与 SignalLog 所属基金不一致 | 拒绝回应 | `SIGNAL_FUND_MISMATCH` |
| SignalLog 已有关联未软删交易 | 拒绝重复回应 | `SIGNAL_ALREADY_RESPONDED` |
| 日历日期已存在 | 返回 0，不抛异常 | 无 |
| 手动确认缺少交易发生日净值 | 保持 PENDING，拒绝使用最新净值 | `NAV_HISTORY_EMPTY` |
| 单只基金补偿失败 | 记录错误并继续其他基金 | 无 |

## 5. Good / Base / Bad Cases

- Good：A、B 当日净值齐备，一次事务确认两腿并生成 B lot。
- Good：周一创建的补录交易将 `tradeDate` 设为上周五，确认时使用周五净值而非周一创建时间。
- Good：月计划日落在月末连续休市区间，下月首个交易日补执行一次。
- Good：当日定投已 CONFIRMED 或 CANCELLED，Job 重跑仍不新增交易。
- Good：已回应的逻辑止损信号重跑后保留原信号和原交易。
- Good：03:00 时周五净值缺失，周五下午历史净值补齐后由提交后事件立即确认，不等待下周一。
- Good：应用在 cron 后启动，启动补偿扫描确认所有已具备交易日净值的历史 PENDING。
- Good：同一信号并发回应时锁内只允许第一笔创建交易。
- Base：历史 A 已确认、B 待确认，只用 A 已有净额确认 B。
- Base：50 份 open lot + 50 份 ADJUST_IN，卖 100 份时仅前 50 份计算赎回费。
- Base：日历空表日常同步执行全量初始化，非空表只处理最大日期之后。
- Bad：北京时间 03:00 直接按 JVM UTC 日期减一天，会再偏一天。
- Bad：所有 PENDING 交易共用 Job 日期，会把周末积压交易按后续净值确认。
- Bad：手动确认直接读取最新一期净值，会把历史交易按后续交易日价格成交。
- Bad：把用户选择的发生日写入 `createdDate`，Spring 审计保存时会覆盖为当前时间。
- Bad：定投幂等只查 PENDING，确认或撤销后重跑会重复扣款。
- Bad：在净值写入事务内同步确认，确认异常会把已抓到的净值一起回滚。
- Bad：创建 PENDING 卖出时提前设 CLEARED，撤单后基金状态与事实持仓不一致。
- Bad：先 `findAll()` 再 `save()` 日历，多实例会同时判定不存在并撞唯一索引。

## 6. Tests Required

- `ChinaTradingDateTest` / `NavConfirmJobTest`：覆盖北京时间凌晨与前一自然日标签。
- `PendingTransactionCompensationJobTest`：覆盖启动补偿、每小时 cron 和上海时区。
- `PendingTransactionCompensationServiceTest`：覆盖按基金去重、单只失败隔离和继续确认其他基金。
- `DailyNavConfirmServiceEventTest` / `MarketDataFetchServiceDateTest`：断言新增净值后发布 `FundNavUpdatedEvent`。
- `NavConfirmServiceStateTest`：覆盖交易自身日期、周末旧交易、缺净值保持 PENDING 和转换两腿原子确认。
- `FundTransactionServiceTest`：覆盖历史 `tradeDate`、未来日期拒绝和转换两腿日期一致。
- `SignalOperationServiceUnitTest` / `SignalOperationServiceTest`：覆盖归属、重复回应、SELL 关联、PENDING 状态和非正实际值。
- `SignalQueryServiceTest`：已回应信号不再出现在 pending 列表。
- `SignalGenerationServiceTest` / `FundTransactionRepositoryTest`：无买入记录仍落信号，较新卖出不覆盖最近买入时间，已回应信号重跑不覆盖。
- `DcaPlanServiceTest` / `DcaSuggestionJobTest`：覆盖参数范围、月末跨月顺延和 PENDING/CONFIRMED/CANCELLED 全状态幂等。
- `DisciplineStrategyServiceTest`：覆盖生效策略未触发卖出返回 `NO_SELL_TRIGGER`。
- `FundPositionService` 调用路径测试：确认/撤销后按 CONFIRMED 事实持仓重算状态。
- `TransactionConfirmServiceStateTest`：CONFIRMED/PENDING 不重复调用 `onSellConfirmed`。
- `TransactionCancelServiceStateTest`：关联腿已确认时拒绝撤销。
- `TransactionConfirmSupportTest`：部分 lot 缺口、ADJUST_OUT、初始持仓 lot 且不重复扣费。
- `TradingCalendarSchemaIntegrationTest`：原子重复写返回 1/0，最大日期查询正确。
- `TradingCalendarSyncServiceTest`：空表全量、非空增量和管理全量补写。
- 前端生产构建必须通过；推送后 GitHub CI 必须全绿。

## 7. Wrong vs Correct

### Wrong

```java
confirmOne(outLeg); // 不检查 outLeg 是否已 CONFIRMED
Instant tradeDay = clock.instant().minus(1, DAYS).truncatedTo(DAYS); // JVM UTC 口径
fund.setStatus(CLEARED); // PENDING 阶段提前改事实状态
if (!existing.contains(date)) repository.save(entity); // 并发竞态
tx.setCreatedDate(request.tradeDate()); // 审计字段会被保存流程覆盖
repository.existsByDcaPlanIdAndStatusAndCreatedDateBetween(id, PENDING, start, end); // 确认/撤销后可重复生成
```

### Correct

```java
if (outLeg.getStatus() == PENDING) confirmOne(outLeg);
Instant tradeDay = ChinaTradingDate.previousUtcDate(clock.instant());
Instant txTime = tx.getTradeDate() != null ? tx.getTradeDate() : tx.getCreatedDate();
Instant txDay = ChinaTradingDate.toUtcDate(txTime != null ? txTime : tradeDay);
fundPositionService.reconcileStatus(fundId); // 只在确认/撤销后按事实重算
repository.insertTradingDayIfAbsent(date); // INSERT ... ON CONFLICT DO NOTHING
eventPublisher.publishEvent(new FundNavUpdatedEvent(fundId)); // AFTER_COMMIT 再推进交易
repository.existsByDcaPlanIdAndTradeDateBetween(id, start, end); // 任意状态均防重
```

## Scenario: DCA Take-Profit Lifecycle

### 1. Scope / Trigger

- Trigger: changes to trailing take-profit signals, confirmed/cancelled SELL transactions, FIFO lots, or strategy activation.

### 2. Signatures

```java
TakeProfitEvaluation TakeProfitLifecycleService.prepare(
    FundEntity fund, FundStrategyEntity strategy,
    BigDecimal currentNav, BigDecimal holdingShares, Instant today);
void TakeProfitLifecycleService.bindTriggeredSignal(FundStrategyEntity strategy, Long signalId);
void TakeProfitLifecycleService.onTransactionConfirmed(FundTransactionEntity transaction);
void TakeProfitLifecycleService.onTransactionCancelled(FundTransactionEntity transaction);
GET /api/funds/{fundId}/strategies/recommendation
```

`fund_strategy` owns the preset metadata and runtime fields: `profit_activation_percent`, `profit_harvest_percent`,
`minimum_holding_percent`, `max_single_sell_percent`, `cooldown_trading_days`, `preset_fund_category`, `preset_version`,
`customized`, `take_profit_phase`, `cycle_started_at`, `cycle_peak_nav`, `triggered_signal_id`, `cooldown_started_at`.

### 3. Contracts

- Percentages are positive ratios (`0.06` means 6%).
- Recommendation uses `FundCategory`, never `FundSubType`; user values are changed only by explicit save/restore.
- `ACCUMULATING -> ARMED` records the current NAV and cannot sell on the same day.
- A `TRIGGERED` cycle keeps its original actionable SignalLog; daily reruns must not replace it with NONE.
- Both `NavConfirmService` and `TransactionConfirmService` call the lifecycle after confirming a trailing-stop transaction.
- `TransactionCancelService` restores the matching cycle to `ARMED`.
- Mature shares are calculated per open `fund_lot`; a recent DCA lot cannot freeze older lots.

### 4. Validation & Error Matrix

| Condition | Behavior | ErrorCode |
|---|---|---|
| Missing fund category for recommendation | Reject strategy create/update | `FUND_CATEGORY_REQUIRED` |
| Missing/out-of-range strategy ratio | Reject request | `STRATEGY_PARAM_INVALID` |
| Take-profit transaction confirmed | Enter `COOLDOWN` | none |
| Matching PENDING transaction cancelled | Restore `ARMED` | none |
| Cost, NAV, or holding shares missing/non-positive | Do not arm take-profit | none |

### 5. Good/Base/Bad Cases

- Good: 60 mature shares + 20 recent shares + 20 untracked adjustment shares -> 80 shares are eligible.
- Good: cooldown finishes while return is still above activation -> arm at today's NAV and wait for a new drawdown.
- Base: user leaves a triggered signal unanswered -> keep one pending signal and do not create daily duplicates.
- Bad: use the latest INVEST confirm time as a fund-wide lock -> daily DCA disables take-profit forever.

### 6. Tests Required

- `TakeProfitPresetServiceTest`: assert all four category templates and custom detection.
- `TakeProfitLifecycleServiceTest`: assert arming day, new high, mature lot calculation, confirm/cancel, and cooldown rearm.
- `DisciplineStrategyServiceTest`: assert the four sell caps and logic-stop priority.
- `SignalGenerationServiceTest`: assert a triggered cycle preserves its actionable signal on rerun.
- Both confirmation service tests must verify lifecycle notification after transaction persistence.

### 7. Wrong vs Correct

#### Wrong

```java
lastBuy = latestConfirmedInvest();
if (daysSince(lastBuy) < 5) return NONE; // freezes every old lot
shares = holdingShares.multiply(new BigDecimal("0.25")); // repeats every day
```

#### Correct

```java
matureShares = sumOpenLotsHeldAtLeastFiveTradingDays();
shares = min(profitHarvestShares, singleSellCapShares, matureShares, retentionCapShares);
takeProfitLifecycleService.onTransactionConfirmed(tx);
```
