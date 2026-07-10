# 设计：核心交易与行情一致性修复

## 1. 统一交易日期标签

新增共享日期工具，将任意 Instant 按 `Asia/Shanghai` 解释为业务自然日，再映射成数据库 DATE 契约使用的 UTC 00:00 Instant。`NavConfirmJob`、`NavConfirmService`、`MarketRealtimeRefreshJob` 和 `FundPnlService` 共用该工具，避免各自实现日期转换。

`NavConfirmService` 对每笔交易使用 `createdDate` 推导净值日期，方法参数仅作为旧数据缺失时间时的 fallback。转换两腿继续在同一事务内使用同一交易日原子确认。

## 2. 信号回应边界

- Repository 提供 SignalLog 悲观写锁查询，串行化同一信号的并发回应。
- 锁内检查是否已有对应 FundTransaction；存在则抛业务异常。
- Service 接收 `fundId` 并校验 SignalLog 归属。
- 待确认查询使用 `NOT EXISTS FundTransaction`，最多返回 100 条。
- SELL/BUILD/ADD 统一把 SignalLog 写入交易。

不新增数据库唯一索引，避免本任务触发 schema 变更；悲观锁 + 同事务 exists 检查保证当前多实例数据库边界内的幂等。

## 3. 状态重算

在 `FundPositionService` 增加统一状态重算入口：查询 CONFIRMED 交易和净份额后决定 `PENDING_HOLDING/HOLDING/CLEARED`。交易确认和撤销服务均调用此入口。信号确认只创建 PENDING 交易，不再提前推进状态。

## 4. ETF 行情映射

现有 `VolumeStateCalculator` 的 `HIGH_DROP` 已同时表达“超过 1.5 倍均量 + 当日收跌”。因此不新增字段，`SignalGenerationService` 将 `benchmarkDroppedToday` 从 `volumeState == HIGH_DROP` 派生，保持现有快照 schema。

## 5. 初始持仓 lot

`FundService` 将初始持仓交易的 `confirmTime` 设为最终 `openedAt`，先保存交易取得 ID，再调用 `TransactionConfirmSupport` 新增专用的初始持仓 lot 记录方法。该方法不计算申购费、不重算已由用户提供的成本价，只建立后续赎回 FIFO 所需事实。

## 6. 行情流水线

- 复用共享日期工具修正交易日和今日净值判断。
- `MarketRealtimeCache` 刷新全部未软删基金估值。
- 14:50 只保留一个调度入口：先执行 `fetchBatch(2)`，成功返回后再生成信号；14:30/14:40 批次保持不变。

## 7. 日历同步

Repository 提供最大日期查询。`sync()` 作为日常增量入口，只写最大日期之后的数据；`syncFull()` 保留管理补写。空表下二者行为一致。仍使用数据库 `ON CONFLICT DO NOTHING` 保证并发幂等。

## 8. 启动与费率时序

`MarketRealtimeCache` 启动时只刷新指数/板块/资金三类批量接口，不枚举基金估值。`FundFeeRefreshJob` 移除启动监听，改为北京时间 02:30 定时刷新，确保在 03:00 交易确认前准备费率缓存。

## 9. 最短持有期查询

仓储查询限定 `status=CONFIRMED` 且 `source in (INCREASE, TRANSFER_IN, INVEST)`，按 `confirmTime desc` 取一条。信号生成每只基金只查询一次，复用于 `CapitalContext` 和交易日计数；无记录返回 null，由策略编排按已满足窗口处理。

## 10. 回滚与风险

- 全部为 Java/查询逻辑调整，无 schema 和依赖变化，可通过回滚提交恢复。
- 悲观锁只锁单条 SignalLog，持锁时间为一次短事务。
- 全基金估值会增加观察池数量对应的外部请求，但仍受共享 2 req/s 限流和 30 秒后台刷新保护。
- 启动后基金估值最迟在交易时段下一个 30 秒周期出现；非交易时段不需要盘中估值。
