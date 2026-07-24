# Implement: 定投计划管理页

## Execution Order

1. 提取按计划的本月剩余预测服务，并让预算摘要复用。
2. 增加全局计划管理 View、查询接口和 repository fetch join。
3. 允许 EFFECTIVE 计划直接更新，补状态与预测回归测试。
4. 增加前端全局 hook、管理页、路由与导航，复用编辑 Modal。
5. 更新文案、领域文档和 transaction-consistency spec。
6. 运行后端聚焦测试、前端 lint/test/build 和 Trellis 全量检查。

## Validation

- Backend: DcaPlanServiceTest, DcaBudgetSummaryServiceUnitTest, DcaScheduleServiceTest, 新预测服务测试。
- Frontend: npm run lint, npm test, npm run build。
- CI: Java 25 + PostgreSQL 16 全量 verify。

## Rollback

- 无 schema 变更；回滚代码即可恢复旧页面和接口行为。
