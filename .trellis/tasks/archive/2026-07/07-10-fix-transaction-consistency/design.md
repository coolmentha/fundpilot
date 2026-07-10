# 设计：交易确认与账本一致性修复

## 1. 夜间确认日期

`NavConfirmJob` 注入全局 UTC `Clock`，执行时计算 `Instant.now(clock).minus(1, DAYS).truncatedTo(DAYS)`，显式传给 `NavConfirmService`。Job 使用 `@RequiredArgsConstructor`，同时修复构造器规范问题。

## 2. 转换状态矩阵

### 批量确认

- PENDING/PENDING：预先读取两腿当日净值；任一缺失则全部跳过；均存在时依次确认转出和转入。
- CONFIRMED/PENDING：兼容历史半状态，使用已确认转出净额，只确认转入腿。
- PENDING/CONFIRMED：视为非法历史状态，记录错误并跳过，避免覆盖已确认转入数据。

### 手动确认/撤销

- `TransactionConfirmService` 只对 PENDING 腿调用 `confirmOne`。
- `TransactionCancelService` 在转换关联腿已确认时拒绝撤销，避免半撤销。

## 3. ADJUST 与 lot

- `TransactionConfirmSupport.onAdjustConfirmed`：ADJUST_OUT 按 FIFO 减少 open lot，仅维护 `remainingShares`，不创建 `FundLotRedemptionEntity`；ADJUST_IN 不建 lot。
- 卖出确认先计算“卖出前事实持仓”和 open lot 总额。open lot 消耗完仍有剩余时，仅当剩余不超过未跟踪事实份额时允许零费率降级，否则继续抛 `INSUFFICIENT_LOTS`。

## 4. 输入校验

后端统一校验 `signum() > 0`；前端 `InputNumber` 设置 `min={0.01}`。不依赖前端保证后端数据正确。

## 5. 日历并发幂等

`TradingCalendarRepository` 增加 PostgreSQL `INSERT ... ON CONFLICT ... DO NOTHING` 原子写入方法。`TradingCalendarSyncService` 不再执行全表 `findAll`，直接累计每个日期的插入返回值。

## 6. 验证

- 先补回归测试并确认旧实现失败。
- 修复后运行聚焦测试、后端 `verify`、前端 `build`。
- 提交并推送当前 feature 分支，使用 `gh` 持续检查对应 Actions，失败则读取日志、修复并重新推送，直到通过。
