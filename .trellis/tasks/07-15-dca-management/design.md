# Design: 定投计划管理页

## Backend

- 新增 `DcaPlanForecastService`，统一计算当前月各 EFFECTIVE/enabled 计划尚未生成交易的执行日期与金额。
- `DcaBudgetSummaryService` 使用预测服务汇总 `futureAmount`，管理列表也使用同一结果，避免口径漂移。
- `FundDcaPlanRepository` 提供带 Fund fetch join 的全量计划查询，避免序列化阶段 N+1。
- 新增全局 `GET /api/dca-plans`，返回平铺 View：计划字段、基金名称/代码、本月剩余次数/金额/日期。
- `PUT /api/dca-plans/{id}` 改为允许 EFFECTIVE 和 DRAFT 更新；验证和频率字段归一化保持不变。

## Frontend

- 新增 `DcaManagementPage`，顶部为紧凑预算摘要，下方使用单层数据表展示全部计划。
- 复用 `DcaPlanFormModal`，不复制计划表单逻辑。
- 新增全局 plans query hook；所有计划 mutation 成功后同时失效全局列表、基金详情计划和预算摘要。
- 桌面表格显示完整列；移动端使用响应式隐藏次要列并保留基金、计划、剩余金额、状态和操作。
- 视觉沿用现有深色 Ant Design 工作台，不引入依赖或新的配色系统。

## Compatibility

- 不修改数据库 schema。
- 旧基金详情接口和操作端点保留。
- View 新增独立类型，不改变旧 `FundDcaPlanView` 响应结构。

## Risks

- 预测与 Job 分叉：由共享 `DcaScheduleService` 和新的唯一预测服务消除。
- 编辑与 14:55 Job 同时发生：数据库事务保证单次更新原子；已生成交易由现有唯一索引和任意状态幂等检查兜底。
- 全局列表加载 lazy Fund：repository fetch join 在事务内一次取齐。
