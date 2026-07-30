# 后端 DDD 与 Spring Modulith 改造规划

## Goal

将 FundPilot 后端从按技术层组织、跨业务包直接依赖的单体，逐步改造成以领域边界为核心、由 Spring Modulith 持续验证模块契约的模块化单体。改造后应提高业务规则内聚度与模块自治性，并保证现有数据库数据可完整迁移和对账；旧 REST/Java API 不作为兼容目标，前后端按新接口同步切换。

## Background

- 后端是 Spring Boot 4.0.0、Java 25 的单个 Maven 应用，约有 267 个主 Java 文件。
- 当前顶层包为 `fund`、`market`、`strategy`、`signal`、`dca`、`portfolio`、`user` 等，每个包内部仍主要按 `controller/service/repository/entity` 分层。
- 当前存在 `fund <-> market`、`fund <-> strategy`、`fund <-> signal`、`signal <-> strategy` 等双向依赖；跨包代码直接引用其他包的 Entity、Repository、Service 和 Controller DTO。
- `FundEntity` 同时承载全局产品资料、用户归属、纪律分类、持仓状态、成本和提醒配置；`FundArchiveService` 会跨多个业务包级联软删；`FundView` 同时组合产品、持仓、行情和盈亏。
- 当前未引入 Spring Modulith，没有 `package-info.java` 模块声明和模块结构验证测试。
- 当前 Git 分支为 `main`，工作区仅有本 Trellis 规划目录。业务代码和正式领域文档的修改必须在用户授权创建并切换的 `feature/*` 分支上进行。

## Domain Boundaries

| 模块 | 拥有的业务能力和数据 |
| --- | --- |
| `IdentityAccess` | 用户、凭据、角色、启停状态、登录会话、当前操作者 |
| `ProductCatalog` | `FundProduct`、产品类型、基金字典、产品搜索、跟踪指数、费率、默认纪律分类建议 |
| `Portfolio` | 用户级 `PortfolioFund`、观察关系、分组、仓位提醒、作废状态 |
| `Accounting` | 交易、费用、FIFO lot、赎回明细、`Position`、成本、账目纠错 |
| `MarketData` | 已公布净值、盘中估值、K 线、指标、交易日历、用户关注指数 |
| `Discipline` | 最终纪律分类、策略、止盈周期、建议、建议日志和建议回应 |
| `InvestmentPlan` | 定投计划、启停、调度、月度预算、未来现金流预测 |
| `Insights` | 综合基金视图、当前组合、已清仓历史、累计收益和趋势 |
| `Importing` | 养基宝防腐层、会话、预览、冲突处理、导入执行和重试 |
| `platform/sharedkernel` | 严格受限的技术基础，以及极少数稳定跨领域值对象 |

`admin` 不是领域模块，也不是 Adapter 的一级业务职责包。管理 Controller 与普通 Controller 一样，按所属上下文和业务职责进入 `adapter.web.<business-capability>`；类名可体现管理入口。管理入口仍须与普通入口明确隔离，但不要求保留现有 `/api/admin/**` 的具体 URL。

## Business Requirements

### 产品、组合基金与持仓

- 当前 `Fund` 必须拆为全局共享的 `FundProduct` 和用户组合中的 `PortfolioFund`。
- `FundProduct` 以基金代码标识，承载名称、产品类型、投资标的、跟踪指数和行情关联等全局事实。
- `PortfolioFund` 以用户和 `FundProduct` 的关系标识，承载观察关系、分组、仓位提醒和有效性；无事实持仓时也可存在。
- `Position` 归 `Accounting`，由 CONFIRMED 交易账本派生，统一承载事实份额、在途份额、建仓时间和成本单价。上述字段不得继续属于 `PortfolioFund`。
- 页面模型可以组合 `FundProduct`、`PortfolioFund`、`Position` 和行情，不要求单一领域实体覆盖全部展示字段。

### 作废与清仓

- “作废”用于纠正基金代码、初始持仓等录入错误，表示该 `PortfolioFund` 及其相关录入从未构成有效投资事实。
- 作废保留产品、组合关系、账目、纪律和计划的底层审计记录，不执行跨模块级联软删。
- 作废后，相关记录必须从持仓、盈亏、纪律和计划计算中完全排除；模块可保存自己的失效投影，但不得直接修改其他模块的数据表。
- 作废需要记录操作者、时间和原因，并要求显式二次确认。作废不可恢复；误作废时只能重新建立新的 `PortfolioFund`，原记录继续保留审计。
- “清仓”表示有效投资历史的当前事实份额归零，与作废严格区分。
- 清仓基金默认不出现在当前基金列表，但交易历史和已实现收益继续计入累计总收益，并可在独立“历史/已清仓”视图查询。
- 后续有效买入可使已清仓基金重新进入当前组合；作废不能通过新增交易自动恢复。

### 分类与纪律

- `FundSubType` 的规范术语为“产品类型”，归 `ProductCatalog`。
- `FundCategory` 的规范术语为“纪律分类”，最终值及用户自定义状态归 `Discipline`。
- `ProductCatalog` 只提供默认纪律分类建议；目录建议变化不得覆盖用户已确认或自定义的纪律配置。
- 当前 `strategy` 与 `signal` 合并为 `Discipline`。
- 原“信号执行”统一称为“建议回应”。用户接受建议只表示请求创建账目交易，不代表 FundPilot 代替用户在外部基金平台执行交易。

### 交易、净值与定投

- `Accounting` 独占交易、费用、lot、成本和持仓规则，不直接访问其他模块的 Repository 或 Entity。
- `MarketData` 独占已公布单位净值、累计净值和盘中估值。盘中估值不得用于交易确认。
- `Accounting` 通过 `MarketData` 的公开契约取得交易日单位净值，并固化确认交易使用的净值快照；通过 `ProductCatalog` 的公开契约取得费率。
- 新净值发布可触发待确认交易处理，但行情写入和交易确认不得共享内部持久化对象。
- `InvestmentPlan` 独立于 `Discipline`，拥有计划规则、调度、月度预算和预测。
- 到达执行日后，`InvestmentPlan` 通过 `Accounting` 公开命令创建 `INVEST/PENDING`，使用计划 ID 与交易日组成的业务幂等键。
- `InvestmentPlan` 通过 `MarketData` 公开契约判断交易日，不访问行情内部表。
- `Accounting` 只保存计划来源引用，不拥有计划规则；`InvestmentPlan` 不访问交易 Repository。

### 身份、管理与配置

- `IdentityAccess` 向业务模块公开最小化 `CurrentActor` 契约。
- 管理员可访问普通入口和需要 `ADMIN` 角色的管理入口。
- 管理员调用普通接口时仍只访问自己的数据；跨用户操作必须通过管理入口并显式指定目标用户。
- 禁止用 `userId = 0` 等隐式约定表达全局权限。
- 解散通用 `UserConfig`：月度定投预算归 `InvestmentPlan`，关注指数归 `MarketData`。
- 只有未来出现具备独立规则的跨模块后台流程时，才考虑建立 `Backoffice` 编排模块。

### 查询与导入

- `Insights` 是只读叶子模块，负责基金综合列表/详情、当前组合、组合汇总、收益、趋势和历史快照。
- `Insights` 可依赖各模块公开只读契约；初期允许同步组合，后续可按性能需要由事件维护专用投影。
- 核心模块不得反向依赖 `Insights`；展示 DTO 不得作为跨模块写契约。
- `Importing` 是系统边缘的防腐层，只能通过 `IdentityAccess`、`ProductCatalog`、`Portfolio` 和 `Accounting` 的公开契约导入。
- 核心业务模块不得依赖 `Importing`；更换导入提供商不得改变核心领域模型。

## Architecture Requirements

- 使用 Spring Modulith 2.0.x 与当前 Spring Boot 4.0.x 基线兼容，不在本次重构中夹带 Boot 升级。
- 采用应用根包的直接子包作为模块，使用 `@ApplicationModule(allowedDependencies = ...)` 声明允许依赖，使用 `@NamedInterface` 暴露 `api` 和 `events` 契约。
- 顶层业务模块与限界上下文一一对应。`domain` 下的职责包只能包含本限界上下文内部的聚合、规则和值对象；任何跨上下文对象都必须留在其所有者模块，通过调用方 Gateway、被调用方模块 API 和集成事件协作。
- 每个业务模块内部固定采用四个一级层包：`adapter`、`application`、`domain`、`infrastructure`。依赖方向为 `adapter -> application -> domain`；`infrastructure` 实现 domain repository 和 application 定义的 Gateway，domain 不依赖其他三层。Adapter 先按入口协议分为 `api`、`web`、`scheduler`、`event`，再在协议包内按业务职责分包，不建立统一 `admin` 业务包。Application 的 `command`、`query`、`gateway`、`event` 同样先按功能类别、再按业务职责分包。
- 跨模块公开命令与查询采用模块 API 形式，放在 `adapter.api` 并通过 `@NamedInterface("api")` 暴露。模块 API 是无传输协议的公开入站 Adapter，携带不可变请求/结果类型并直接委派本模块 Handler。聚合产生的内部领域事件与聚合放在同一职责包；application 将需要跨模块传播的事件转换为 `application.event` 集成事件，并通过 `@NamedInterface("events")` 暴露。不得为了公开契约增加第五个一级层包。
- 事件发布固定为“发布方 Handler -> `application.event` 集成事件 -> `infrastructure.messaging` 持久化发布”；事件订阅固定为“订阅方 `adapter.event` -> 订阅方 Handler”。Application 不得直接监听其他模块事件，Adapter 不得直接调用消息发布基础设施。
- 本模块 Web、Scheduler、Event Adapter 直接调用对应的 `XxxCommandHandler` 或 `XxxQueryHandler`；跨模块同步调用固定为“调用方 Handler -> 调用方 `application.gateway` -> 调用方 `infrastructure.gateway` GatewayImpl -> 被调用方 `adapter.api` -> 被调用方 Handler”。application 不直接引用其他上下文。聚合仓储抽象与聚合放在同一职责包，禁止使用泛化 `Port` 命名。
- Gateway 的方法、入参和返回值必须使用调用方上下文的语言和类型；只有 GatewayImpl 可以引用被调用方 `adapter.api` 的请求/结果类型并完成双向转换。
- Gateway 与 GatewayImpl 的包名和类型名也必须使用调用方业务职责，不得采用目标模块的聚合或 API 名称。
- 同步 Gateway 调用链必须加入调用方的同一本地事务，任一步失败整体回滚，`GatewayImpl -> adapter.api -> Handler` 不得使用 `REQUIRES_NEW`；事件只在源事务提交后交给消费者，消费者各自以独立事务幂等处理。
- 被调用方 `adapter.api` 只暴露自身稳定业务错误；GatewayImpl 负责将可预期失败转换为调用方错误语义，调用方 Handler 不得引用目标模块错误码或异常类型。
- Web Adapter 从认证上下文解析 `actor`，管理入口另解析显式 `subject`；写命令与模块 API 请求必须显式携带这些审计/授权上下文，Handler 负责授权与业务校验。Scheduler 与 Event Adapter 使用明确且可审计的系统执行者，Handler 与模块 API 不得直接读取 HTTP 或 `SecurityContext`。
- 模块 API 按业务职责拆分，不按命令/查询拆分；同一职责 API 可以同时包含读写方法。禁止建立模块级万能 API 或只反映技术操作类型的 `CommandApi`、`QueryApi`。
- Application 内部读写分离：写用例进入 `application.command` 并按职责聚合为 `XxxCommandHandler`，读用例进入 `application.query` 并按职责聚合为 `XxxQueryHandler`。各类入站 Adapter 直接向对应 Handler 薄委派；不采用每个用例一个 Handler。本次只做代码职责分离，不默认拆读写数据库或复制两套领域模型。
- Domain 必须完全纯净，不得引用 JPA、Spring、Spring Modulith、Jackson、Lombok 或 infrastructure 类型。JPA Entity、Spring Data Repository 和 Domain/Persistence Mapper 全部归 `infrastructure.persistence`。
- Domain 按聚合职责分包，不建立统一 `model/repository/service/event` 技术分包。聚合根、实体、值对象、仓储抽象和内部事件共同放在所属聚合包中。
- `infrastructure.persistence` 同样按聚合职责分包。JPA Entity、Spring Data 接口、Domain/Persistence Mapper 和 Repository 实现共同放在对应聚合的 persistence 包中，禁止统一 `entity/repository/mapper` 技术分包。
- Infrastructure 统一采用“技术能力 -> 职责”两级分包：`persistence/<aggregate>`、`gateway/<calling-capability>`、`remote/<external-capability>`、`cache/<business-capability>`、`messaging/<publishing-capability>`；`configuration` 只放模块配置。禁止 `gateway.impl`、`remote.client` 等跨职责技术大包。
- 禁止跨模块引用 Domain Entity、Repository、内部 Service、Handler 和内部 DTO；跨模块同步调用只能由调用方 `infrastructure.gateway` 中的 GatewayImpl 使用对方公开的 `adapter.api`。
- 必须消除业务模块循环依赖。同步写流程只能沿允许依赖方向调用；后续反应使用持久化、幂等的 Modulith 事件。
- 关键事件必须使用 Event Publication Registry 记录发布状态，支持失败重试、重放和监控；监听器必须按业务幂等键实现幂等。
- 可重建缓存刷新可采用尽力通知，但不得承担唯一业务事实。
- 使用 `ApplicationModules.of(FundPilotBackendApplication.class).verify()` 建立架构门禁；迁移期仅显式纳管新模块，并维护不断缩小的 legacy 例外清单，最终切换为全部直接子包自动发现。
- 使用 `@ApplicationModuleTest` 和 `Scenario` 覆盖模块公开契约和事件协作。
- `platform` 与 `sharedkernel` 是四层业务模块之外的受限例外。`platform` 只承载 HTTP/异常映射、持久化技术基类、时钟、事务、调度和可观测性，且不得依赖业务模块；`sharedkernel` 只承载稳定 ID、值对象和极少数真正共享的规则，保持纯 Java，禁止放入 Entity、Repository、业务 Service、模块 DTO、业务枚举、Spring 或 JPA 类型，也不得依赖 `platform` 或业务模块。
- 业务错误码由所属模块定义；平台层只依赖统一业务异常契约并映射到 HTTP。
- 引入 Spring Modulith 依赖和事件发布表都必须在实施前单独获得用户确认。事件表由 Flyway 管理，生产环境继续使用 `ddl-auto=validate`，不得依赖运行时自动建表。

## Compatibility And Delivery Constraints

- 迁移不保证现有 REST URL、请求/响应 JSON、错误码或 Java API 兼容；新接口按领域职责设计，前后端在对应切片同步切换。
- 普通入口与管理入口必须保持权限语义隔离；具体路径可重新设计。
- 数据迁移采用 expand/migrate/contract 顺序：先增加结构和回填/对账能力，再切换所有者，最后删除旧字段或旧表。任何阶段不得丢失有效历史数据。
- 每个纵向切片必须具备独立测试、数据校验、可观测性和停止点，不进行一次性全量搬迁。
- schema、依赖、认证授权、公共 API、批量移动和目录重命名均需在实际执行前按项目规则再次确认。

## Acceptance Criteria

- [ ] AC-01：每个模块都有明确职责、数据所有权、公开模块 API、事件和禁止依赖。
- [ ] AC-02：现有跨业务包依赖被归类为合法同步调用、领域事件、查询组合或待消除耦合，并且最终不存在循环依赖。
- [ ] AC-03：`ApplicationModules.verify()` 在 CI 中通过，且不存在新模块到 legacy 内部包的反向引用。
- [ ] AC-04：关键模块具有 `@ApplicationModuleTest`，关键事件可验证发布、消费失败、重试和幂等。
- [ ] AC-05：同一基金产品可被多个用户独立观察或持有；产品资料和用户级配置不再混在同一聚合中。
- [ ] AC-06：错误录入可作废并留下审计记录；作废记录不进入持仓、盈亏、纪律或计划计算。
- [ ] AC-07：清仓基金退出当前列表、进入已清仓历史，并继续计入累计总收益；再次买入可重新进入当前组合。
- [ ] AC-08：交易确认只使用交易日已公布单位净值，确认快照、费用、lot、成本和 Position 可审计。
- [ ] AC-09：管理员普通入口仍按自身数据隔离，跨用户操作只允许通过显式管理入口。
- [ ] AC-10：数据库迁移可在生产副本演练，迁移前后核心数据对账一致，并具备前滚恢复方案。
- [ ] AC-11：形成并通过用户评审的 `design.md` 和 `implement.md`；未批准前不执行 `task.py start`。
- [ ] AC-12：Adapter 按入口协议和业务职责分包；本模块 Web/Scheduler/Event Adapter 直接调用 Command/Query Handler，其他上下文经调用方 Gateway/GatewayImpl 和目标模块 `adapter.api` 进入，Application 不直接跨上下文依赖。
- [ ] AC-13：调用方 Gateway 的公开签名不泄漏目标模块 API DTO；GatewayImpl 是唯一负责调用目标 API 并进行模型转换的类型。

## Out Of Scope

- 当前规划阶段不修改业务代码、正式领域文档、依赖、数据库 schema、API 或部署配置。
- 目标形态首先是模块化单体，不拆微服务、不引入分布式事务。
- 不为形式上的 DDD 分层重写清晰稳定的简单 CRUD。
- 不在本次改造中升级 Spring Boot、Java、数据库或前端框架。
