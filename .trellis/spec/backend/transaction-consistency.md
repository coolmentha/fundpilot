# Transaction Consistency

## 1. Scope / Trigger

适用于手工交易输入、夜间净值确认、基金转换两腿状态、ADJUST 份额修正、FIFO lot 和交易日历同步。任何改动同时触及交易状态与 lot、或定时任务与 DATE 存储时，必须按本契约检查。

## 2. Signatures

```java
int NavConfirmService.confirmPendingTransactions(Instant tradeDayUtc);
List<FundTransactionEntity> TransactionConfirmService.confirm(Long transactionId);
List<FundTransactionEntity> TransactionCancelService.cancel(Long transactionId);
void TransactionConfirmSupport.onAdjustConfirmed(FundTransactionEntity tx);
int TradingCalendarRepository.insertTradingDayIfAbsent(Instant calendarDate);
```

前端 `POST /api/funds/{fundId}/transactions`：买入类传正数 `amount`，卖出/调整类传正数 `shares`，转换可额外传 `targetFundId`。

## 3. Contracts

- `NavConfirmJob` 次日 03:00 传前一日 UTC 00:00，不传当前时刻。
- 转换 PENDING/PENDING 仅在两腿同日净值都存在时原子确认：转出 -> 净额回填 -> 转入。
- 历史 CONFIRMED/PENDING 只补确认转入腿，不重复执行转出 FIFO。
- 转换任一腿已确认时，不允许撤销另一腿。
- ADJUST_OUT 按 FIFO 缩减 open lot，不算费、不写赎回明细；ADJUST_IN 不建收费 lot。
- 卖出存在 lot 缺口时，只有卖出前事实持仓中确有未跟踪份额，缺口才按零赎回费降级。
- 交易日历使用数据库 `ON CONFLICT DO NOTHING` 原子插入，不使用“先查后插”实现幂等。

## 4. Validation & Error Matrix

| 条件 | 行为 | ErrorCode |
|---|---|---|
| 买入 amount 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 卖出/调整 shares 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 转换转入已确认、转出仍待确认 | 拒绝继续确认 | `ILLEGAL_STATE_TRANSITION` |
| 转换关联腿已确认，撤销另一腿 | 拒绝半撤销 | `TRANSACTION_ALREADY_CONFIRMED` |
| lot 缺口大于事实未跟踪份额 | 交易不确认 | `INSUFFICIENT_LOTS` |
| 日历日期已存在 | 返回 0，不抛异常 | 无 |

## 5. Good / Base / Bad Cases

- Good：A、B 当日净值齐备，一次事务确认两腿并生成 B lot。
- Base：历史 A 已确认、B 待确认，只用 A 已有净额确认 B。
- Base：50 份 open lot + 50 份 ADJUST_IN，卖 100 份时仅前 50 份计算赎回费。
- Bad：凌晨 03:00 以 `Instant.now()` 作为查询起点，会排除前一日 UTC 00:00 净值。
- Bad：先 `findAll()` 再 `save()` 日历，多实例会同时判定不存在并撞唯一索引。

## 6. Tests Required

- `NavConfirmJobTest`：固定时钟并断言传入前一日 UTC 00:00。
- `NavConfirmServiceStateTest`：任一转换腿缺净值时两腿保持 PENDING。
- `TransactionConfirmServiceStateTest`：CONFIRMED/PENDING 不重复调用 `onSellConfirmed`。
- `TransactionCancelServiceStateTest`：关联腿已确认时拒绝撤销。
- `TransactionConfirmSupportTest`：部分 lot 缺口降级、ADJUST_OUT FIFO 缩减且无赎回明细。
- `TradingCalendarSchemaIntegrationTest`：同一日期连续原子写入返回 1、0。
- `TradingCalendarSyncServiceTest`：全部解析日期都走原子插入，并只累计返回 1 的写入。
- 前端生产构建必须通过；推送后 GitHub CI 必须全绿。

## 7. Wrong vs Correct

### Wrong

```java
confirmOne(outLeg); // 不检查 outLeg 是否已 CONFIRMED
Instant dayStart = Instant.now(); // 03:00 起查，漏掉前一日 00:00 净值
if (!existing.contains(date)) repository.save(entity); // 并发竞态
```

### Correct

```java
if (outLeg.getStatus() == PENDING) confirmOne(outLeg);
Instant tradeDay = Instant.now(clock).minus(1, DAYS).truncatedTo(DAYS);
repository.insertTradingDayIfAbsent(date); // INSERT ... ON CONFLICT DO NOTHING
```
