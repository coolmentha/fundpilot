# Transaction Consistency

## 1. Scope / Trigger

适用于信号回应、手工交易输入、夜间净值确认、基金状态重算、转换两腿状态、ADJUST/初始持仓 FIFO lot 和交易日历同步。任何改动触及 PENDING/CONFIRMED 状态、lot、定时任务或 DATE 存储时，必须按本契约检查。

## 2. Signatures

```java
int NavConfirmService.confirmPendingTransactions(Instant tradeDayUtc);
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
```

前端 `POST /api/funds/{fundId}/transactions`：买入类传正数 `amount`，卖出/调整类传正数 `shares`，转换可额外传 `targetFundId`。

## 3. Contracts

- `ChinaTradingDate` 是北京时间自然日到数据库 UTC 00:00 DATE 标签的唯一转换入口。
- `MarketDataFetchService` 写 snapshot、`SignalQueryService.today` 查当日信号也必须使用同一日期标签，手动入口不能按 JVM UTC 截日。
- `NavConfirmJob` 次日 03:00 用 `ChinaTradingDate.previousUtcDate(clock.instant())` 传前一业务自然日标签。
- `NavConfirmService` 优先按每笔 PENDING 交易的 `createdDate` 选择净值日；Job 参数只为历史空时间降级。
- SignalLog 回应必须校验路径基金归属，并在悲观锁内检查未软删关联交易，保证同一信号只生成一笔交易。
- `MIN_HOLD_DAYS` 只查最近一笔 CONFIRMED 的 INCREASE/TRANSFER_IN/INVEST；卖出、调整和 PENDING 不得重置持有期。
- 创建 PENDING 交易不得提前修改基金状态；确认和撤销后统一调用 `reconcileStatus` 按 CONFIRMED 净份额重算。
- 转换 PENDING/PENDING 仅在两腿同日净值都存在时原子确认：转出 -> 净额回填 -> 转入。
- 历史 CONFIRMED/PENDING 只补确认转入腿，不重复执行转出 FIFO。
- 转换任一腿已确认时，不允许撤销另一腿。
- ADJUST_OUT 按 FIFO 缩减 open lot，不算费、不写赎回明细；ADJUST_IN 不建收费 lot。
- 初始持仓同步确认时必须创建 open lot，`confirmTime` 使用最终 `openedAt`，且不重复扣申购费。
- 卖出存在 lot 缺口时，只有卖出前事实持仓中确有未跟踪份额，缺口才按零赎回费降级。
- 交易日历使用数据库 `ON CONFLICT DO NOTHING` 原子插入，不使用“先查后插”实现幂等。
- 日常 `sync()` 空表全量、非空表只写最大日期之后；管理 `syncFull()` 遍历全量以补历史缺口。

## 4. Validation & Error Matrix

| 条件 | 行为 | ErrorCode |
|---|---|---|
| 买入 amount 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 卖出/调整 shares 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 转换转入已确认、转出仍待确认 | 拒绝继续确认 | `ILLEGAL_STATE_TRANSITION` |
| 转换关联腿已确认，撤销另一腿 | 拒绝半撤销 | `TRANSACTION_ALREADY_CONFIRMED` |
| lot 缺口大于事实未跟踪份额 | 交易不确认 | `INSUFFICIENT_LOTS` |
| 路径 fundId 与 SignalLog 所属基金不一致 | 拒绝回应 | `SIGNAL_FUND_MISMATCH` |
| SignalLog 已有关联未软删交易 | 拒绝重复回应 | `SIGNAL_ALREADY_RESPONDED` |
| 日历日期已存在 | 返回 0，不抛异常 | 无 |

## 5. Good / Base / Bad Cases

- Good：A、B 当日净值齐备，一次事务确认两腿并生成 B lot。
- Good：周一补确认上周五 PENDING 交易时按交易 `createdDate` 使用周五净值。
- Good：同一信号并发回应时锁内只允许第一笔创建交易。
- Base：历史 A 已确认、B 待确认，只用 A 已有净额确认 B。
- Base：50 份 open lot + 50 份 ADJUST_IN，卖 100 份时仅前 50 份计算赎回费。
- Base：日历空表日常同步执行全量初始化，非空表只处理最大日期之后。
- Bad：北京时间 03:00 直接按 JVM UTC 日期减一天，会再偏一天。
- Bad：所有 PENDING 交易共用 Job 日期，会把周末积压交易按后续净值确认。
- Bad：创建 PENDING 卖出时提前设 CLEARED，撤单后基金状态与事实持仓不一致。
- Bad：先 `findAll()` 再 `save()` 日历，多实例会同时判定不存在并撞唯一索引。

## 6. Tests Required

- `ChinaTradingDateTest` / `NavConfirmJobTest`：覆盖北京时间凌晨与前一自然日标签。
- `NavConfirmServiceStateTest`：覆盖交易自身日期、周末旧交易、缺净值保持 PENDING 和转换两腿原子确认。
- `SignalOperationServiceUnitTest` / `SignalOperationServiceTest`：覆盖归属、重复回应、SELL 关联和 PENDING 状态。
- `SignalQueryServiceTest`：已回应信号不再出现在 pending 列表。
- `SignalGenerationServiceTest` / `FundTransactionRepositoryTest`：无买入记录仍落信号，较新卖出不覆盖最近买入时间。
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
```

### Correct

```java
if (outLeg.getStatus() == PENDING) confirmOne(outLeg);
Instant tradeDay = ChinaTradingDate.previousUtcDate(clock.instant());
Instant txDay = ChinaTradingDate.toUtcDate(tx.getCreatedDate() != null ? tx.getCreatedDate() : tradeDay);
fundPositionService.reconcileStatus(fundId); // 只在确认/撤销后按事实重算
repository.insertTradingDayIfAbsent(date); // INSERT ... ON CONFLICT DO NOTHING
```
