# 实施计划

1. 在 `EastmoneyJsParserNavHistoryTest` 和 `EastmoneyClientIntegrationTest` 中换用真实 `16:00Z` 时间戳，先确认旧实现失败。
2. 在 `EastmoneyJsParser` 解析边界调用 `ChinaTradingDate.toUtcDate`，使解析器测试转绿。
3. 新增 `V21__normalize_fund_nav_dates.sql`，归一化存量日期、软删除重复活动行并恢复唯一索引。
4. 新增独立 schema 的 Flyway 集成测试：先迁移到 V20、插入偏移/重复数据、再迁移到最新版本，校验日期、保留规则、唯一索引与
   Flyway validate。
5. 更新交易一致性规范和 ADR-0006，记录外部日期边界契约与 V21 修复。
6. 运行解析器定向测试、迁移集成测试、后端完整测试和 Trellis 质量检查。
