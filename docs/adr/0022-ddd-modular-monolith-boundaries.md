# DDD modular monolith boundaries with Spring Modulith

FundPilot 将从按 MVC 技术角色组织的单体迁移为 Spring Modulith 约束的 DDD 模块化单体。业务边界固定为 IdentityAccess、ProductCatalog、Portfolio、Accounting、MarketData、Discipline、InvestmentPlan、Insights 和 Importing；每个业务模块内部使用 `adapter/application/domain/infrastructure` 四层，domain 保持纯 Java。该选择以清晰的业务所有权和可执行的模块依赖检查换取迁移成本，并避免把当前跨包调用原样包装成“DDD”。

## Status

Accepted.

## Synchronous collaboration

模块内 Web、Scheduler 和 Event Adapter 直接调用本模块的 CommandHandler 或 QueryHandler。跨模块同步调用由调用方定义业务语义明确的 Application Gateway，并由调用方 Infrastructure GatewayImpl 转换后调用被调用方 `adapter.api`；`adapter.api` 是 Spring Modulith Named Interface `api`，再委托被调用方 Handler。Gateway 和参数结果使用调用方语言，目标 API DTO、错误码和异常只允许出现在 GatewayImpl。

同步链路共享同一本地事务，禁止使用 `REQUIRES_NEW` 人为切断原子性。模块间异步协作通过发布方 `application.event` 暴露的 Named Interface `events`；发布由 infrastructure.messaging 完成，订阅方从 adapter.event 进入自己的 Handler，并负责独立事务与幂等。

## Boundary constraints

- Domain 不依赖 Spring、JPA、Jackson、Lombok或其他模块。
- `platform` 只承载技术能力且不依赖业务模块；`sharedkernel` 只容纳稳定的纯 Java ID、值对象和极少数共享规则。
- 前端请求从 adapter.web 进入；服务间调用也必须经过被调用模块 adapter.api，不能直接依赖其 Handler、Domain 或 Repository。
- Web Adapter 解析 actor；管理入口显式传入 subject。管理员调用普通接口仍按自身身份访问，跨用户操作只允许从 `/api/admin/**` 进入。

## Persistence and events

迁移只保证现有数据库数据可迁移、可对账且不丢失，不保证旧 REST 路径、请求响应结构、错误码或 Java API 兼容。前后端按新领域接口同步切换，并通过表所有权清单逐步把持久化实现归入唯一业务模块。持久化事件不作为本次迁移的默认模型；只有跨事务、确需最终一致性的协作才使用集成事件，同一业务原子操作优先使用同步 Gateway 链路。

## Considered Options

- 继续 MVC 三层：无法表达聚合所有权，现有顶层包的双向依赖会继续扩散。
- 直接拆为微服务：当前部署和事务边界仍适合单体，过早引入远程调用与分布式一致性会放大迁移风险。
- 一次性重写：难以持续验证数据对账与业务规则，失败时也缺少可定位、可回切的迁移边界。
- 使用公开 Port 或公开 Application Gateway：语义过于抽象，且会让调用方依赖被调用方应用内部结构。
- 服务间直接调用 Handler：绕过 adapter.api 的公开契约，Spring Modulith 无法稳定约束模块边界。
- 全部事件化：增加最终一致性、幂等和排障成本，不适合当前必须原子提交的同步用例。
