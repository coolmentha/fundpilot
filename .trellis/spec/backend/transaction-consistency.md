# Transaction Consistency

## 1. Scope / Trigger

适用于信号回应、手工交易输入、夜间净值确认、基金状态重算、转换两腿状态、ADJUST/初始持仓 FIFO lot、定投预算摘要和交易日历同步。任何改动触及 PENDING/CONFIRMED 状态、lot、定时任务、DATE 存储或展示型风险提示时，必须按本契约检查。

## 2. Signatures

```java
int NavConfirmService.confirmPendingTransactions(Instant tradeDayUtc);
int NavConfirmService.confirmPendingTransactionsIsolated(Instant tradeDayUtc);
int NavConfirmService.confirmPendingTransactionsForFund(Long fundId);
int PendingTransactionCompensationService.compensateAll();
int PendingTransactionCompensationService.compensateFund(Long fundId);
List<FundTransactionEntity> TransactionConfirmService.confirm(Long transactionId);
List<FundTransactionEntity> TransactionCancelService.cancel(Long transactionId);
FundTransactionEntity SignalOperationService.confirmOperation(Long fundId, Long signalLogId, ConfirmOperationRequest request);
FundEntity FundPositionService.reconcileStatus(Long fundId);
BigDecimal FundPositionService.getUntrackedHoldingShares(Long fundId);
void TransactionConfirmSupport.onAdjustConfirmed(FundTransactionEntity tx);
void TransactionConfirmSupport.onExistingPositionConfirmed(FundTransactionEntity tx, BigDecimal acquireCostPerShare);
int TradingCalendarRepository.insertTradingDayIfAbsent(Instant calendarDate);
Optional<Instant> TradingCalendarRepository.findMaxCalendarDate();
int TradingCalendarSyncService.sync();
int TradingCalendarSyncService.syncFull();
FundTransactionView FundTransactionService.createManual(Long fundId, ManualTransactionRequest request);
boolean FundTransactionRepository.existsByDcaPlanIdAndTradeDateBetween(Long dcaPlanId, Instant start, Instant end);
DcaBudgetSummaryView DcaBudgetSummaryService.currentMonth();
Map<Long, List<Instant>> DcaPlanForecastService.currentMonthExecutionDates(List<FundDcaPlanEntity> plans);
boolean DcaScheduleService.isFutureExecutionDay(FundDcaPlanEntity plan, Instant candidate, Instant now);
List<FundNavHistoryEntity> FundNavHistoryRepository.findByFundEntity_IdAndNavDateGreaterThanEqualAndNavDateLessThan(
    Long fundId, Instant startInclusive, Instant endExclusive);
List<SignalLogEntity> SignalLogRepository.findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(
    Long fundId, Instant startInclusive, Instant endExclusive);
<T> T RequiresNewTransactionExecutor.execute(Supplier<T> work);
```

前端 `POST /api/funds/{fundId}/transactions`：买入类传正数 `amount`，卖出/调整类传正数 `shares`，转换可额外传 `targetFundId`；
可选 `tradeDate: Instant` 表示真实交易发生时间，省略时后端使用当前时间。

`PUT /api/user-config` 同时覆盖 `watchedIndices: string[]` 与可空 `monthlyDcaBudget: decimal`；
`GET /api/dca/budget-summary` 返回 `monthlyBudget/investedAmount/futureAmount/projectedAmount/remainingAmount/overBudgetAmount`。
`GET /api/dca-plans` 返回全部计划及基金信息，并附当前月剩余次数、金额和预计执行日期。

数据库 `fund_transaction.trade_date TIMESTAMPTZ` 保存业务发生时间；`created_date` 继续由 Spring 审计维护。V16 用 `created_date` 回填存量行，
并建立 `(fund_id, trade_date DESC) WHERE deleted_date IS NULL` 索引。
V22 删除 `user_config.total_capital`，新增可空 `monthly_dca_budget`；将 `fund.max_position_ratio` 重命名为
`position_warning_ratio` 并新增 `position_warning_enabled`，保留存量阈值。

## 3. Contracts

- `ChinaTradingDate` 是北京时间自然日到数据库 UTC 00:00 DATE 标签的唯一转换入口。
- `MarketDataFetchService` 写 snapshot、`SignalQueryService.today` 查当日信号也必须使用同一日期标签，手动入口不能按 JVM UTC 截日。
- `NavConfirmJob` 次日 03:00 用 `ChinaTradingDate.previousUtcDate(clock.instant())` 传前一业务自然日标签。
- `tradeDate` 是业务交易发生时间，`createdDate` 只是审计创建时间；所有新建交易路径必须显式写 `tradeDate`。
- `NavConfirmService` 优先按每笔 PENDING 交易的 `tradeDate` 选择净值日；仅存量 `tradeDate` 为空时回退 `createdDate`，Job 参数是最后降级值。
- 手动确认与自动确认都必须按交易 `tradeDate` 对应的北京时间自然日取单位净值；累计净值仅用于复权分析，禁止用于真实交易份额、金额和市值。
- 单日净值查询必须使用半开区间 `[startInclusive, endExclusive)`；禁止 `Between` 或包含结束点的查询，避免当日缺净值时用次日净值确认历史交易。
- 单日/日期范围信号查询也必须使用半开区间；历史列表和同日重跑都不得把次日 `00:00` 信号纳入当前日。
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
- 历史 CONFIRMED 交易的 `tradeDate` 若落在非交易日，重建使用该日期之前最近一期单位净值，禁止使用未来净值；onboarding lot 仍保留旧 `acquireDate` 作为持有期起点。
- onboarding 用户成本从重建前基金总成本扣除普通 lot 成本后反推，不得用旧交易净值覆盖。
- 同一定投计划同一北京时间自然日由部分唯一索引最终兜底，Job 使用 `ON CONFLICT DO NOTHING` 原子生成。
- DCA Job 只负责交易日门控、基金遍历和失败隔离；每只基金必须调用独立 Spring Bean 的 `@Transactional` Service，禁止同 Bean 自调用事务方法。
- 行情抓取、信号生成、夜间净值确认等按基金遍历的定时批处理，每只基金必须通过 `RequiresNewTransactionExecutor` 或等价的代理 Bean 在独立事务中执行；单只失败只回滚当前基金并继续后续基金。
- 卖出存在 lot 缺口时，只有按 CONFIRMED 账本 FIFO 重放后确有剩余 `ADJUST_IN` 未跟踪份额，缺口才按零赎回费降级；普通买入存在但 open lot 全空属于账本损坏。
- 所有 SELL 确认入口在消费 lot 前必须先悲观锁定基金行，再基于 CONFIRMED 交易汇总校验事实持仓；不得依赖请求前页面持仓、缓存持仓或仅校验 lot 总数。
- `monthlyDcaBudget` 是可选展示预算，不是余额或买入额度；预算为空时仍返回已定投、未来计划和预计定投，但剩余/超额为空。
- 本月已定投统计北京时间自然月内所有非 CANCELLED 的 INVEST，包含手动/自动和 PENDING/CONFIRMED。
- 本月剩余预计只含 EFFECTIVE 且 enabled 的计划；当天仅在 14:55 前算未来，同一计划已有任意状态交易的实际执行日不得重复计入，月计划跨月顺延按实际月份归属。
- 预算摘要和全局计划列表必须共用 `DcaPlanForecastService`；计划列表的剩余金额合计必须等于摘要 `futureAmount`。
- EFFECTIVE 与 DRAFT 计划都允许修改参数；修改只影响尚未生成的未来交易，不改写历史 PENDING/CONFIRMED/CANCELLED。
- `positionWarningEnabled/positionWarningRatio` 只比较当前 CONFIRMED 持仓市值占全部当前持仓市值的比例；关闭后仍可展示比例，不告警。任一已持仓基金当前市值未知时，所有比例都保持未知，禁止按可用子集重算。
- 所有买入确认入口（INCREASE/TRANSFER_IN/INVEST、初始持仓和转换转入腿）不得读取月度预算或仓位提醒字段，也不得因预算超额或占比超线抛业务错误。
- ADJUST 分支先悲观锁基金行；`ADJUST_OUT` 不得超过 CONFIRMED 事实持仓，交易、lot 更新和 `reconcileStatus` 必须位于同一事务。
- 交易日历使用数据库 `ON CONFLICT DO NOTHING` 原子插入，不使用“先查后插”实现幂等。
- 日常 `sync()` 空表全量、非空表只写最大日期之后；管理 `syncFull()` 遍历全量以补历史缺口。
- DCA 周计划只接受周一至周五；月计划允许连续休市后跨月顺延到首个交易日。
- DCA 幂等按 `dcaPlanId + tradeDate` 的北京时间自然日范围检查任意状态；CONFIRMED/CANCELLED 都不得重建本期交易。
- 普通信号仅在当前日期之前最近一个交易日有效；当前策略绑定的 `TRIGGERED` 止盈信号可跨日回应。
- 信号操作状态统一投影为 `INFORMATIONAL/PENDING/RESPONDED/IGNORED/EXPIRED`；已忽略或过期信号不得确认，同日重跑不得覆盖已忽略信号。

## 4. Validation & Error Matrix

| 条件 | 行为 | ErrorCode |
|---|---|---|
| 买入 amount 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 卖出/调整 shares 为空、为零或负数 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 手动交易 `tradeDate` 晚于当前时间 | 拒绝创建 | `MANUAL_TRANSACTION_FIELD_REQUIRED` |
| 定投金额非正、频率为空、周计划日不在 1..5、月计划日不在 1..28 | 拒绝创建/更新/激活 | `DCA_PLAN_INVALID` |
| EFFECTIVE 或 DRAFT 计划参数合法 | 原计划原状态更新，只影响未来未生成交易 | 无 |
| 信号回应实际金额或份额为零/负数 | 拒绝回应 | `SIGNAL_OPERATION_VALUE_INVALID` |
| 转换转入已确认、转出仍待确认 | 拒绝继续确认 | `ILLEGAL_STATE_TRANSITION` |
| 转换关联腿已确认，撤销另一腿 | 拒绝半撤销 | `TRANSACTION_ALREADY_CONFIRMED` |
| lot 缺口大于事实未跟踪份额 | 交易不确认 | `INSUFFICIENT_LOTS` |
| 路径 fundId 与 SignalLog 所属基金不一致 | 拒绝回应 | `SIGNAL_FUND_MISMATCH` |
| SignalLog 已有关联未软删交易 | 拒绝重复回应 | `SIGNAL_ALREADY_RESPONDED` |
| SignalLog 已忽略 | 拒绝回应 | `SIGNAL_ALREADY_IGNORED` |
| SignalLog 已过期 | 拒绝回应 | `SIGNAL_EXPIRED` |
| ADJUST_OUT 超过 CONFIRMED 事实持仓 | 拒绝创建 | `INSUFFICIENT_HOLDING_SHARES` |
| 日历日期已存在 | 返回 0，不抛异常 | 无 |
| 手动确认缺少交易发生日净值 | 保持 PENDING，拒绝使用最新净值 | `NAV_HISTORY_EMPTY` |
| SELL 份额超过锁后计算的 CONFIRMED 事实持仓 | 交易不确认，不消费 lot | `INSUFFICIENT_HOLDING_SHARES` |
| 月度定投预算为空 | 返回金额摘要，不显示进度/超额 | 无 |
| 月度定投预算非正或超过金额精度 | 拒绝配置更新 | `MONTHLY_DCA_BUDGET_INVALID` |
| 仓位提醒线不在 `(0, 1]` | 拒绝基金创建/更新 | `POSITION_WARNING_RATIO_INVALID` |
| 预计定投超预算或当前占比超提醒线 | 仅 UI 提示，交易继续生成/确认 | 无 |
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
- Good：基金 A 行情抓取失败并回滚，基金 B 仍在独立事务中成功落库。
- Good：卖出确认锁定基金后重新汇总 CONFIRMED 事实持仓，拒绝并发请求造成的第二次超卖。
- Good：转换转入腿具备当日单位净值后正常确认，即使未设置预算或基金当前占比超过提醒线。
- Good：14:54 的当日计划计入未来金额，14:55 后由实际 INVEST 交易进入已定投；同一日期不重复相加。
- Good：全局计划列表逐计划剩余金额之和等于预算摘要 `futureAmount`，CANCELLED 日期在两处都不重复预测。
- Good：直接修改 EFFECTIVE 计划金额后，历史交易金额不变，后续尚未生成日期使用新金额。
- Base：历史 A 已确认、B 待确认，只用 A 已有净额确认 B。
- Base：50 份 open lot + 50 份 ADJUST_IN，卖 100 份时仅前 50 份计算赎回费。
- Base：日历空表日常同步执行全量初始化，非空表只处理最大日期之后。
- Bad：北京时间 03:00 直接按 JVM UTC 日期减一天，会再偏一天。
- Bad：所有 PENDING 交易共用 Job 日期，会把周末积压交易按后续净值确认。
- Bad：手动确认直接读取最新一期净值，会把历史交易按后续交易日价格成交。
- Bad：用 `[dayStart, nextDayStart]` 查询单日净值，会在当日缺失时命中次日边界值。
- Bad：批处理外围包一层大事务，基金 B 的失败会回滚基金 A 已完成的结果。
- Bad：先读取持仓再确认卖出，两个并发请求都可能基于同一旧持仓通过校验。
- Bad：把用户选择的发生日写入 `createdDate`，Spring 审计保存时会覆盖为当前时间。
- Bad：定投幂等只查 PENDING，确认或撤销后重跑会重复扣款。
- Bad：在净值写入事务内同步确认，确认异常会把已抓到的净值一起回滚。
- Bad：创建 PENDING 卖出时提前设 CLEARED，撤单后基金状态与事实持仓不一致。
- Bad：先 `findAll()` 再 `save()` 日历，多实例会同时判定不存在并撞唯一索引。
- Bad：在 `TransactionConfirmSupport` 或初始持仓创建中读取预算/提醒字段并拒绝买入，会把展示偏好错误升级为账本状态阻断。
- Bad：管理页按基金循环调用 `/api/funds/{id}/dca-plans`，形成 N+1 请求并让摘要与逐计划日期使用两套预测逻辑。

## 6. Tests Required

- `ChinaTradingDateTest` / `NavConfirmJobTest`：覆盖北京时间凌晨与前一自然日标签。
- `PendingTransactionCompensationJobTest`：覆盖启动补偿、每小时 cron 和上海时区。
- `PendingTransactionCompensationServiceTest`：覆盖按基金去重、单只失败隔离和继续确认其他基金。
- `DailyNavConfirmServiceEventTest` / `MarketDataFetchServiceDateTest`：断言新增净值后发布 `FundNavUpdatedEvent`。
- `NavConfirmServiceStateTest`：覆盖交易自身日期、周末旧交易、缺净值保持 PENDING 和转换两腿原子确认。
- `NavConfirmAndCancelServiceTest` / `SellConfirmationHoldingValidationTest`：覆盖结束点排除、手动/自动/转换卖出的锁后事实持仓校验和并发超卖保护。
- `FundTransactionServiceTest`：覆盖历史 `tradeDate`、未来日期拒绝和转换两腿日期一致。
- `SignalOperationServiceUnitTest` / `SignalOperationServiceTest`：覆盖归属、重复回应、SELL 关联、PENDING 状态和非正实际值。
- `SignalQueryServiceTest`：已回应信号不再出现在 pending 列表。
- `SignalGenerationServiceTest` / `FundTransactionRepositoryTest`：无买入记录仍落信号，较新卖出不覆盖最近买入时间，已回应信号重跑不覆盖。
- `DcaPlanServiceTest` / `DcaSuggestionJobTest`：覆盖 EFFECTIVE 直接更新、参数范围、月末跨月顺延和 PENDING/CONFIRMED/CANCELLED 全状态幂等。
- `DcaPlanForecastServiceTest` / `DcaBudgetSummaryServiceTest`：覆盖逐计划日期、任意状态交易去重、停用/暂停过滤，以及逐计划金额与摘要口径一致。
- `DisciplineStrategyServiceTest`：覆盖生效策略未触发卖出返回 `NO_SELL_TRIGGER`。
- `FundPositionService` 调用路径测试：确认/撤销后按 CONFIRMED 事实持仓重算状态。
- `TransactionConfirmServiceStateTest`：CONFIRMED/PENDING 不重复调用 `onSellConfirmed`。
- `TransactionCancelServiceStateTest`：关联腿已确认时拒绝撤销。
- `TransactionConfirmSupportTest` / `FundPositionServiceUnitTest`：部分 lot 缺口、全空 lot 的合法 ADJUST_IN/损坏账本分支、账本 FIFO 重放、ADJUST_OUT、初始持仓 lot 且不重复扣费。
- `DcaBudgetSummaryServiceTest` / `DcaScheduleServiceTest`：覆盖 PENDING/CONFIRMED 计入、CANCELLED 排除、手动 INVEST、14:55 边界、已生成日期去重、跨月顺延、预算为空/剩余/超额。
- `FundServiceAutoFetchTest` / `TransactionConfirmSupportTest` / 转换确认测试：覆盖初始持仓和所有买入确认不受预算/仓位提醒影响。
- `TradingCalendarSchemaIntegrationTest`：原子重复写返回 1/0，最大日期查询正确。
- `TradingCalendarSyncServiceTest`：空表全量、非空增量和管理全量补写。
- `RequiresNewTransactionExecutorTest` 及各批处理 Service/Job 测试：断言每只基金使用 `REQUIRES_NEW`，单基金异常不回滚或阻断其他基金。
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
repository.findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(id, start, end); // Between 包含结束点
for (FundEntity fund : funds) confirmOne(fund); // 外围大事务导致整批回滚
onSellConfirmed(tx, nav); // 未锁基金、未按 CONFIRMED 事实持仓复核
positionLimitService.validatePurchase(tx, nav); // 展示型提醒错误进入确认路径
funds.stream().map(fund -> get("/api/funds/" + fund.id() + "/dca-plans")); // 管理页 N+1 且自行重算剩余金额
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
navRepository.findByFundEntity_IdAndNavDateGreaterThanEqualAndNavDateLessThan(id, start, end); // [start, end)
requiresNewTransactionExecutor.execute(() -> processFund(fundId)); // 每只基金独立提交/回滚
fundRepository.findByIdForUpdate(fundId);
validateAgainstConfirmedHolding(fundId, tx.getShares());
dcaBudgetSummaryService.currentMonth(); // 预算只由只读摘要和 UI 消费
dcaPlanForecastService.currentMonthExecutionDates(plans); // 摘要和全局计划列表共用逐计划预测
```

## Scenario: Fund NAV Date Normalization

### 1. Scope / Trigger

- Trigger: changes to external NAV parsers, `FundNavSnapshot.navDate`, `fund_nav_history.nav_date`, NAV upsert logic, or migrations that rewrite NAV dates.

### 2. Signatures

```java
List<FundNavSnapshot> EastmoneyJsParser.parseNavHistory(String rawJs);
Instant ChinaTradingDate.toUtcDate(Instant source);
```

```sql
fund_nav_history.nav_date TIMESTAMPTZ
V21__normalize_fund_nav_dates.sql
```

### 3. Contracts

- `FundNavSnapshot.navDate` is the UTC `00:00` label for the corresponding `Asia/Shanghai` natural date; it is not the source timestamp preserved verbatim.
- External epoch values are normalized once at the parser boundary. Market services, repositories, and transaction confirmation consume the normalized contract and must not repeat source-specific conversion.
- A source instant already at UTC `00:00` remains unchanged when it belongs to the same Beijing natural date.
- V21 normalizes existing non-null NAV dates, soft-deletes duplicate active rows by `fund_id + normalized nav_date`, and recreates `uq_fund_nav_history_daily` in one Flyway transaction.
- Duplicate retention order is: more complete `nav`/`accumulated_nav`, newer `updated_date`, then larger `id`.
- The migration never changes transaction status. Startup/hourly compensation confirms PENDING transactions after migrated NAV rows become queryable.
- Single-day transaction lookup remains the strict half-open interval `[dayStart, nextDayStart)`.

### 4. Validation & Error Matrix

| Condition | Behavior | Result |
|---|---|---|
| Eastmoney timestamp is Beijing midnight (`16:00Z` on the prior UTC date) | Normalize to the Beijing date's UTC `00:00` label | Snapshot accepted |
| Timestamp is already the correct UTC `00:00` label | Keep the same label | Idempotent |
| Multiple active rows normalize to one fund/day | Keep the ranked winner and soft-delete the rest | Unique active row |
| `nav_date` is null in legacy data | Leave it null | Not indexed as a day |
| Any V21 statement or index recreation fails | Roll back the whole Flyway migration | Application startup fails |

### 5. Good / Base / Bad Cases

- Good: `2026-07-12T16:00:00Z` from Eastmoney becomes `2026-07-13T00:00:00Z`, so a July 13 transaction finds unit NAV `1.0407`.
- Good: shifted and already-normalized rows collide; the complete row stays active and the incomplete row is soft-deleted.
- Base: a UTC `00:00` snapshot stays unchanged and upsert remains idempotent.
- Bad: persist `Instant.ofEpochMilli(x)` directly; transaction confirmation looks in the next UTC date bucket and leaves the transaction PENDING.
- Bad: loosen the repository query to include the prior day; that hides corrupt storage and can use the wrong trading day's NAV.

### 6. Tests Required

- `EastmoneyJsParserNavHistoryTest`: assert real `16:00Z` epochs normalize to consecutive UTC `00:00` labels and already-normalized input is idempotent.
- `EastmoneyClientIntegrationTest`: assert the HTTP client exposes the normalized snapshot contract.
- `FundNavDateNormalizationMigrationTest`: migrate an isolated PostgreSQL schema from V20 to V21; assert normalized dates, duplicate soft-delete priority, unique-index enforcement, and strict Flyway validation.
- Full backend test run must apply V21 on a fresh schema and pass Hibernate validation.

### 7. Wrong vs Correct

#### Wrong

```java
Instant date = Instant.ofEpochMilli(sourceEpoch); // persists prior-day 16:00Z
```

#### Correct

```java
Instant date = ChinaTradingDate.toUtcDate(Instant.ofEpochMilli(sourceEpoch));
```

```sql
date_trunc('day', nav_date AT TIME ZONE 'Asia/Shanghai') AT TIME ZONE 'UTC'
```

## Scenario: DCA Take-Profit Lifecycle

### 1. Scope / Trigger

- Trigger: changes to trailing take-profit signals, confirmed/cancelled SELL transactions, FIFO lots, or strategy activation.

### 2. Signatures

```java
TakeProfitEvaluation TakeProfitLifecycleService.prepare(
    FundEntity fund, FundStrategyEntity strategy,
    BigDecimal currentUnitNav, BigDecimal currentAccumulatedNav,
    BigDecimal holdingShares, Instant today);
void TakeProfitLifecycleService.bindTriggeredSignal(FundStrategyEntity strategy, Long signalId);
void TakeProfitLifecycleService.onTransactionConfirmed(FundTransactionEntity transaction);
void TakeProfitLifecycleService.onTransactionCancelled(FundTransactionEntity transaction);
void TakeProfitLifecycleService.onSignalIgnored(SignalLogEntity signal);
GET /api/funds/{fundId}/strategies/recommendation
```

`fund_strategy` owns the preset metadata and runtime fields: `profit_activation_percent`, `profit_harvest_percent`,
`minimum_holding_percent`, `max_single_sell_percent`, `cooldown_trading_days`, `preset_fund_category`, `preset_version`,
`customized`, `take_profit_phase`, `cycle_started_at`, `cycle_peak_nav`, `triggered_signal_id`, `cooldown_started_at`.

### 3. Contracts

- Percentages are positive ratios (`0.06` means 6%).
- Overall return, floating profit, and suggested sell shares use unit NAV; cycle peak and drawdown use accumulated NAV. Both NAV values must come from the same latest `FundNavHistoryEntity`; the intraday snapshot remains logic-stop input and must not be mixed into take-profit calculation. Missing either NAV disables the evaluation without cross-fallback.
- Recommendation uses `FundCategory`, never `FundSubType`; user values are changed only by explicit save/restore.
- `ACCUMULATING -> ARMED` records the current NAV and cannot sell on the same day.
- A `TRIGGERED` cycle keeps its original actionable SignalLog; daily reruns must not replace it with NONE.
- Both `NavConfirmService` and `TransactionConfirmService` call the lifecycle after confirming a trailing-stop transaction.
- `TransactionCancelService` restores the matching cycle to `ARMED`, clears `triggeredSignalId`, `cycleStartedAt`, and `cyclePeakNav`.
- Ignoring the matching trailing-stop signal performs the same reset. The next `prepare` call records the current accumulated NAV as a new cycle peak and cannot sell on that call.
- Mature shares are calculated per open `fund_lot`; a recent DCA lot cannot freeze older lots.

### 4. Validation & Error Matrix

| Condition | Behavior | ErrorCode |
|---|---|---|
| Missing fund category for recommendation | Reject strategy create/update | `FUND_CATEGORY_REQUIRED` |
| Missing/out-of-range strategy ratio | Reject request | `STRATEGY_PARAM_INVALID` |
| Take-profit transaction confirmed | Enter `COOLDOWN` | none |
| Matching PENDING transaction cancelled | Restore `ARMED`, clear old cycle start/peak | none |
| Matching trailing-stop signal ignored | Restore `ARMED`, clear old cycle start/peak | none |
| Cost, NAV, or holding shares missing/non-positive | Do not arm take-profit | none |

### 5. Good/Base/Bad Cases

- Good: 60 mature shares + 20 recent shares + 20 untracked adjustment shares -> 80 shares are eligible.
- Good: cooldown finishes while return is still above activation -> arm at today's NAV and wait for a new drawdown.
- Good: cancel or ignore a triggered signal -> clear the old peak; the next evaluation arms at today's NAV without immediately repeating the same drawdown.
- Base: user leaves a triggered signal unanswered -> keep one pending signal and do not create daily duplicates.
- Bad: use the latest INVEST confirm time as a fund-wide lock -> daily DCA disables take-profit forever.
- Bad: restore `ARMED` while keeping the old `cyclePeakNav` -> the same pullback generates another trailing-stop signal on the next day.

### 6. Tests Required

- `TakeProfitPresetServiceTest`: assert all four category templates and custom detection.
- `TakeProfitLifecycleServiceTest`: assert arming day, new high, mature lot calculation, confirm/cancel, cooldown rearm, cancel/ignore peak reset, and next-evaluation baseline rebuild.
- `DisciplineStrategyServiceTest`: assert the four sell caps, logic-stop priority, and unit/accumulated NAV divergence.
- `SignalGenerationServiceTest`: assert a triggered cycle preserves its actionable signal on rerun and take-profit receives same-row unit/accumulated NAV.
- `SignalQueryServiceTest`: assert a range ending on day T excludes a signal at T+1 `00:00`.
- Both confirmation service tests must verify lifecycle notification after transaction persistence.

### 7. Wrong vs Correct

#### Wrong

```java
lastBuy = latestConfirmedInvest();
if (daysSince(lastBuy) < 5) return NONE; // freezes every old lot
shares = holdingShares.multiply(new BigDecimal("0.25")); // repeats every day
profitHarvestShares = floatingProfit.divide(snapshot.getCurrentNav()); // snapshot is accumulated NAV
strategy.setTakeProfitPhase(ARMED); // old cyclePeakNav survives and repeats the same trigger
```

#### Correct

```java
matureShares = sumOpenLotsHeldAtLeastFiveTradingDays();
latestNav = navHistoryRepository.findLatest();
profitHarvestShares = floatingProfit.divide(latestNav.getNav());
pullback = cyclePeak.subtract(latestNav.getAccumulatedNav()).divide(cyclePeak);
shares = min(profitHarvestShares, singleSellCapShares, matureShares, retentionCapShares);
takeProfitLifecycleService.onTransactionConfirmed(tx);
resetTriggeredCycle(strategy); // ARMED + clear signal/start/peak; next prepare records a new peak
```
