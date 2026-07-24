# 净值交易日归一化设计

## 数据契约

`FundNavSnapshot.navDate` 和 `fund_nav_history.nav_date` 均使用 UTC 00:00 的日期标签表示北京时间自然日。外部接口可以使用任意时区表达日期，但必须在解析边界完成转换。

```text
东方财富 2026-07-12T16:00:00Z
  -> Asia/Shanghai 2026-07-13 00:00
  -> UTC 日期标签 2026-07-13T00:00:00Z
  -> fund_nav_history
  -> 交易日半开区间查询命中
```

## 代码改动

在 `EastmoneyJsParser.parseNavHistory` 中复用 `ChinaTradingDate.toUtcDate`，不在 `MarketDataFetchService`、
`DailyNavConfirmService` 或两个交易确认服务中重复实现日期转换。这样外部格式由数据源适配层负责，所有消费者继续依赖同一个快照契约。

测试样本改用东方财富真实的北京时间零点毫秒戳，避免 UTC 零点夹具掩盖时区错误。

## 数据迁移

V21 在 Flyway 单事务中执行：

1. 删除 `uq_fund_nav_history_daily`，避免连续日期批量平移时产生瞬时唯一键冲突。
2. 将所有非空 `nav_date` 按 `Asia/Shanghai` 转为自然日，再映射为 UTC 00:00。
3. 对未软删行按 `fund_id + nav_date` 排名，优先保留 `nav`/`accumulated_nav` 更完整、`updated_date` 更新、`id` 更大的记录，其余行写入
   `deleted_date`。
4. 按 V1 原定义重建部分唯一索引。

Flyway 失败会回滚整个迁移。生产回滚依赖发布流程在迁移前创建的 PostgreSQL 备份，不编写不可追踪的反向猜测迁移。

## 风险控制

- 不修改 PENDING/CONFIRMED 状态，避免在迁移事务内执行费用、FIFO lot 或持仓逻辑。
- 不改变严格的交易日查询，防止错误使用次日净值。
- 独立 schema 集成测试从 V20 构造偏移数据和重复数据，再升级到 V21 并严格校验。
- 新鲜 schema 由现有 Spring Boot 集成测试验证全部迁移和 Hibernate 映射。
