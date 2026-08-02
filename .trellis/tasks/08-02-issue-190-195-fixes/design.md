# 技术设计

## 影响范围

只修改 backend 的 Portfolio、MarketData、Discipline、InvestmentPlan 边界及对应测试。没有 schema、依赖、部署配置或前端契约变更。

## 设计决策

### 1. 行情网关在边界过滤作废组合基金

`PortfolioFundApi` 继续保留完整组合基金记录，供生命周期、审计和其他需要知道 VOIDED 状态的路径使用。`OwnedFundProductGatewayImpl` 在把组合基金转换为行情产品前过滤 `Validity.TRACKED`，两条入口共用同一私有转换方法。这样不会扩大 Portfolio API 的语义变化，也能保证作废基金不会触发 `FundProductApi` 查询。

### 2. 网关业务拒绝使用现有稳定错误码

删除定投、策略、建议三个网关的裸 `Rejected` 运行时异常。找不到组合基金使用 `ENTITY_NOT_FOUND`，作废组合基金使用现有 `ILLEGAL_STATE_TRANSITION`，由 `GlobalExceptionHandler` 统一映射 HTTP 400。使用已有错误码可复用前端错误提示和现有全局异常回归，不引入新的跨模块错误码。

### 3. 计划可见集合集中计算

扩展 `PlanPortfolioFundGateway` 提供 `findTrackedByOwner`，并新增 `InvestmentPlanVisibleQueryHandler`：

1. 从 Portfolio 获取当前用户的 TRACKED 组合基金 ID 集合。
2. 从 InvestmentPlan 获取该用户的全部计划。
3. 仅保留 portfolioFundId 位于 TRACKED 集合的计划。

`InvestmentPlanQueryHandler.list` 和 `InvestmentPlanBudgetSummaryQueryHandler.currentMonth` 都依赖该组件，确保列表与预算使用完全相同的计划集合。作废计划继续保留在数据库中，不改变生命周期监听器的审计行为。

### 4. 定投领域异常在命令边界转换

`InvestmentPlan` 继续负责领域规则并抛出原生参数/状态异常；`InvestmentPlanCommandHandler` 在创建、更新、退休、启停的领域调用边界转换异常：

- `IllegalArgumentException` -> `BusinessException(DCA_PLAN_INVALID, message)`
- `IllegalStateException` -> `BusinessException(ILLEGAL_STATE_TRANSITION, message)`

保存动作放在领域校验成功之后，避免非法输入产生持久化副作用。频率解析继续在现有命令边界转换为 `DCA_PLAN_INVALID`。

## 测试策略

- MarketData 网关单测覆盖 legacyFundId 和 portfolioFundId 的 VOIDED 入口，并验证产品 API 未被调用。
- 三个业务网关单测覆盖作废基金入口的异常类型和错误码。
- InvestmentPlanCommandHandler 单测覆盖金额、周/月计划日、DRAFT 退休和启停边界。
- 可见计划查询单测覆盖 TRACKED 与 VOIDED 混合集合；预算摘要测试验证只消费可见计划。
- 保留并运行现有全局异常处理器测试，确认 `BusinessException` 仍映射 400。

## 回滚

代码改动均为可逆 Java/测试文件修改；若回归失败，回退当前分支的工作区改动即可，不涉及数据库迁移和生产数据。
