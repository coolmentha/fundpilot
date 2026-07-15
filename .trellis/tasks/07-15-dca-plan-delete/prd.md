# 定投计划删除

## Goal

允许用户清理不再需要的已停用定投计划，同时保持历史交易账本完整。

## Confirmed Facts

- `FundDcaPlanEntity` 已使用 `@SQLDelete` 和继承的 `@SQLRestriction` 实现软删除。
- `FundTransactionEntity.dcaPlanId` 是可空数值字段，不是数据库外键；删除计划不会删除或破坏历史交易。
- 当前全局定投管理页和基金详情页都能停用计划，但没有删除入口。

## Requirements

- 仅 `DRAFT`（页面状态“已停用”）计划允许删除。
- `EFFECTIVE` 计划无论启用或暂停都必须由后端拒绝删除，不能只依赖前端隐藏按钮。
- 删除使用现有软删除机制，不新增 schema 或迁移。
- 删除计划不得删除、取消或修改已有 `PENDING/CONFIRMED/CANCELLED` 交易。
- 全局定投管理页和基金详情页都为已停用计划提供删除入口。
- 删除前显示确认提示，明确不可恢复且历史交易不受影响。
- 删除成功后刷新全局计划、基金计划、当前生效计划和预算摘要。

## Out of Scope

- 不恢复已删除计划。
- 不允许直接删除运行中或已暂停计划。
- 不删除历史交易或清空交易上的 `dcaPlanId`。

## Acceptance Criteria

- [ ] `DELETE /api/dca-plans/{id}` 仅软删除 DRAFT 计划。
- [ ] 删除不存在计划返回 `DCA_PLAN_NOT_FOUND`。
- [ ] 删除 EFFECTIVE 计划返回明确业务错误，计划保持不变。
- [ ] 删除后默认查询不再返回该计划，历史交易仍保留。
- [ ] 两个前端计划列表只对已停用计划显示删除操作，并有不可恢复确认提示。
- [ ] 删除成功后相关 React Query 缓存全部失效。
- [ ] 后端回归测试、前端测试、lint 和生产构建通过。

