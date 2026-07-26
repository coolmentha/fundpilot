# 技术设计：DDD 模块化单体与 Spring Modulith

## 1. 设计原则

1. 先建立可执行边界，再迁移业务：新增模块一开始就受 Modulith 验证，legacy 包作为显式且持续缩小的迁移债务。
2. 以数据所有权决定模块，不以现有 Controller 或表名决定模块。
3. 同步调用只用于必须立即成功或失败的主流程；提交后的跨模块反应使用持久化事件。
4. 模块之间传 ID、值对象、命令结果和事件，不传 JPA Entity、Repository 或内部 DTO。
5. 数据演进使用 expand/migrate/contract，Flyway 只前滚；回滚应用版本前必须保证旧版本能读取扩展后的 schema。
6. REST 不承担向后兼容；每个纵向切片同步设计新接口并迁移前端，数据迁移与对账才是兼容硬约束。

## 2. 目标包结构

顶层业务包同时是 Spring Modulith 应用模块和 DDD 限界上下文。两者在本项目中一一对应，避免出现“代码模块已隔离但领域语言仍混在一起”的双重边界。

```text
com.fundpilot.backend
├── FundPilotBackendApplication
├── identityaccess
├── productcatalog
├── portfolio
├── accounting
├── marketdata
├── discipline
├── investmentplan
├── insights
├── importing
├── platform
└── sharedkernel
```

`platform` 与 `sharedkernel` 不属于业务限界上下文，也不套用四层目录。`platform` 只提供跨模块技术设施，禁止依赖任何业务模块；`sharedkernel` 仅包含无框架的稳定 ID、值对象和极少数共享规则，禁止依赖 `platform` 或任何业务模块。业务模块可以单向依赖两者。

边界示例：

- `portfolio.domain` 只拥有 `PortfolioFund`、`FundGroup` 及其规则。
- `accounting.domain` 只拥有 `Transaction`、`Lot`、`Position` 和账目规则；Portfolio 页面需要 Position 时必须通过 Accounting 模块 API 查询。
- `productcatalog.domain` 只拥有 `FundProduct` 和费率等产品事实；Portfolio 只保存 `FundProductId`。
- 同一页面或同一业务流程中组合出现，不代表属于同一限界上下文。
- 如果一个职责形成独立语言、不变量、生命周期和数据所有权，应提升为新的顶层上下文，不能塞进现有 `domain` 子包。

每个业务模块内部固定采用四层：

```text
<module>/
├── package-info.java              # @ApplicationModule + allowedDependencies
├── adapter/                       # 入站适配层，先按协议、再按业务职责分包
│   ├── api/                       # @NamedInterface("api")，模块间同步调用入口
│   │   ├── <business-capability-a>/ # 公开 API、请求/结果类型，直接委派 Handler
│   │   └── <business-capability-b>/
│   ├── web/                       # Controller、HTTP Request/Response
│   │   ├── <business-capability-a>/ # 普通与管理 HTTP 入口共同按业务职责归属
│   │   └── <business-capability-b>/
│   ├── scheduler/
│   │   └── <business-capability>/ # 定时任务入口
│   └── event/
│       └── <business-capability>/ # 跨模块事件监听入口
├── application/                   # 应用层，先按功能类别、再按业务职责分包
│   ├── event/
│   │   └── <publishing-capability>/ # @NamedInterface("events")，跨模块集成事件
│   ├── command/
│   │   └── <business-capability>/ # XxxCommandHandler
│   ├── query/
│   │   └── <business-capability>/ # XxxQueryHandler
│   └── gateway/
│       └── <business-capability>/ # 调用方语言的 Gateway
├── domain/                        # 领域层
│   ├── <aggregate-a>/             # 聚合根、实体、值对象、仓储抽象、内部事件
│   ├── <aggregate-b>/             # 另一聚合职责
│   └── <business-capability>/     # 确有必要的跨聚合业务规则
└── infrastructure/                # 基础设施层，先按技术能力、再按职责分包
    ├── persistence/
    │   ├── <aggregate-a>/         # JPA Entity、Spring Data、Mapper、Repository 实现
    │   └── <aggregate-b>/         # 另一聚合的完整持久化实现
    ├── gateway/
    │   └── <calling-capability>/  # GatewayImpl，调用其他模块 API 或外部系统
    ├── remote/
    │   └── <external-capability>/ # Feign/RestClient 外部数据源实现
    ├── cache/
    │   └── <business-capability>/ # Redis/本地缓存实现
    ├── messaging/
    │   └── <publishing-capability>/ # Modulith 发布、幂等和重放实现
    └── configuration/             # 模块专属 Spring 配置
```

Portfolio Domain 的具体形态示例：

```text
portfolio/domain/
├── portfoliofund/
│   ├── PortfolioFund.java
│   ├── PortfolioFundId.java
│   ├── PortfolioFundRepository.java
│   └── PortfolioFundVoided.java
└── fundgroup/
    ├── FundGroup.java
    ├── FundGroupId.java
    └── FundGroupRepository.java
```

对应的 Portfolio persistence：

```text
portfolio/infrastructure/persistence/
├── portfoliofund/
│   ├── PortfolioFundJpaEntity.java
│   ├── PortfolioFundJpaRepository.java
│   ├── PortfolioFundPersistenceMapper.java
│   └── PortfolioFundRepositoryImpl.java
└── fundgroup/
    ├── FundGroupJpaEntity.java
    ├── FundGroupJpaRepository.java
    ├── FundGroupPersistenceMapper.java
    └── FundGroupRepositoryImpl.java
```

四层职责与依赖约束：

- `adapter` 先按 `api`、`web`、`scheduler`、`event` 等入口协议分包，再按业务职责分包；只把输入转换为应用命令/查询，并直接调用本模块对应的 Command/Query Handler，不写业务规则，不访问 Domain、Repository 或 Infrastructure。
- `application` 负责编排用例、权限上下文和事务。command 可以加载并修改聚合，query 不修改领域状态；Handler 不直接引用其他上下文，而只依赖本模块 `application.gateway` 中按业务职责定义的 Gateway。Gateway 的方法、入参和返回值使用调用方语言，不得出现目标模块 API DTO。
- `application.command`、`application.query`、`application.gateway`、`application.event` 均采用“功能类别 -> 业务职责”的两级分包；不得在任一功能类别下堆放跨职责的 Handler、Gateway 或事件类型。
- 事件发布链固定为“发布方 Handler -> `application.event` 集成事件 -> `infrastructure.messaging` 持久化发布”。事件订阅链固定为“订阅方 `adapter.event` -> 订阅方 Handler”；监听器是入站 Adapter，Application 不得直接监听其他模块事件，Adapter 不得直接调用消息发布基础设施。
- `domain` 按聚合职责分包，每个职责包聚合其聚合根、实体、值对象、内部领域事件和仓储抽象；不建立统一 `model/repository/service/event` 技术包，不依赖 adapter/application/infrastructure，也不引用 JPA、Spring、Spring Modulith、Jackson、Lombok、Redis、Feign 等技术类型。
- `infrastructure` 实现 domain 仓储和 application Gateway，承载 JPA、Redis、远程客户端、其他模块 API 调用适配、Modulith 发布实现和模块配置；不能被本模块入站 adapter 或 domain 直接调用。
- `infrastructure` 统一采用“技术能力 -> 职责”两级分包：`persistence/<aggregate>`、`gateway/<calling-capability>`、`remote/<external-capability>`、`cache/<business-capability>`、`messaging/<publishing-capability>`；`configuration` 只容纳模块配置。禁止 `gateway.impl`、`remote.client`、`persistence.entity` 等跨职责技术大包。
- `adapter.api` 是模块面向其他上下文公开的同步入站边界。每个职责 API 是公开 Adapter，携带不可变请求/结果类型并直接委派 `application.command` 或 `application.query` 中对应职责的 Handler；聚合职责包内的 Repository 接口只表达该聚合的持久化需求。禁止 `XxxPort` 和泛化 `port` 包。
- 模块根包不放可被偶然引用的 public 业务类型。跨模块只暴露 `adapter.api` 和 `application.event` Named Interface，不增加第五个一级层，从而保持严格四层。
- `platform` 与 `sharedkernel` 是四层规则的受限例外：前者只放跨模块技术设施，后者只放无框架稳定类型；二者都不得依赖任何业务模块，`sharedkernel` 也不得依赖 `platform`。
- 简单 CRUD 可以减少子包和类数量，但四个一级层包的职责方向不变，不允许把业务逻辑塞进入站或基础设施层。
- `domain..` 禁止引用其他顶层业务模块的任何类型，包括对方 Domain。跨上下文 ID 只能使用 sharedkernel 中已批准的稳定值对象；其余数据由调用方 GatewayImpl 转换为调用方语言。

层内依赖方向：

```text
adapter ───────> application ───────> domain
infrastructure ──────> application.gateway
infrastructure ──────> domain 中的聚合仓储抽象
```

本模块 HTTP 入口的调用链：

```text
portfolio.adapter.web.portfoliofund.PortfolioFundController
    -> portfolio.application.command.PortfolioFundCommandHandler

portfolio.adapter.web.portfoliofund.PortfolioFundQueryController
    -> portfolio.application.query.PortfolioFundQueryHandler
```

Web Controller 直接调用本模块 Handler。模块间同步调用必须经调用方 Gateway/GatewayImpl 进入目标模块的 `adapter.api`，由 GatewayImpl 完成模型转换。

跨模块同步调用必须包装一层。例如 Discipline 接受建议后需要建立账目交易：

```text
discipline.application.command.AdviceCommandHandler
    -> discipline.application.gateway.advice.AdviceTradeGateway
    -> discipline.infrastructure.gateway.advice.AdviceTradeGatewayImpl
    -> accounting.adapter.api.transaction.TransactionApi
    -> accounting.application.command.TransactionCommandHandler
```

`AdviceTradeGateway` 的名称、包路径、命令和结果均使用 Discipline 对“接受建议后记账”的语言；`AdviceTradeGatewayImpl` 是唯一可引用 Accounting `TransactionApi` DTO 的类型，负责双向转换；`TransactionApi` 是 Accounting 的入站 Adapter，直接委派其 Handler。除 GatewayImpl 和目标 API 外，模块不得越层引用。外部数据源采用同样规则，例如 `PublishedNavSource` 由 `infrastructure.remote` 实现，不使用 `MarketDataPort` 之类弱语义名称。

`TransactionApi` 仅暴露 Accounting 稳定的业务错误。`AdviceTradeGatewayImpl` 同时负责把可预期的 Accounting 失败转换为 Discipline 的错误语义；`AdviceCommandHandler` 不得捕获或判断 Accounting 的错误码、异常类型或 DTO。

模块 API 与 Application 读写分离示例：

```text
adapter.api.portfoliofund.PortfolioFundApi
    -> command.portfoliofund.PortfolioFundCommandHandler
    -> query.portfoliofund.PortfolioFundQueryHandler
```

`PortfolioFundCommandHandler` 聚合跟踪、更新和作废等写用例；`PortfolioFundQueryHandler` 聚合详情、列表和有效性检查等读用例。Handler 按业务职责继续拆分，不按单个方法或请求机械拆类。这不是强制完整 CQRS：command 与 query 初期可以使用同一数据库事务资源；只有 Insights 或高负载列表出现明确性能证据时，query 才使用独立只读投影。

Domain 采用完全纯净模型：

- `domain.<aggregate>` 只使用 Java 和 sharedkernel 的稳定值对象，不出现任何框架注解。
- 聚合根、实体、值对象、Repository 接口和内部领域事件放在同一聚合职责包；application 负责将需要跨边界传播的内部事件转换为 `application.event` 集成事件。集成事件不得暴露 Domain Entity。
- 不使用 `domain.model`、`domain.repository`、`domain.service`、`domain.event` 技术分包。跨聚合规则只有形成明确业务概念时才建立相应职责包，不能建立万能 service 包。
- `infrastructure.persistence.<aggregate>` 与 `domain.<aggregate>` 一一对应，在同一职责包中放置 JPA Entity、Spring Data 接口、Domain/Persistence Mapper 和聚合 Repository 实现。禁止统一 `persistence.entity`、`persistence.repository`、`persistence.mapper` 技术分包。
- 模块 API 不返回 Domain Entity，而返回定义在 `adapter.api.<business-capability>` 中的不可变结果类型；跨模块命令也只携带稳定 ID、值对象和输入快照。
- 被调用方 API 的请求/结果类型只表达被调用方语言；调用方 Handler 和 Gateway 不得引用它们，必须由 GatewayImpl 映射为调用方自己的命令/结果类型。
- Gateway 与 GatewayImpl 的包名、类型名和方法名按调用方业务职责确定。例如 Discipline 的 `AdviceTradeGateway` 不得命名为 `TransactionGateway`；只有 GatewayImpl 内部依赖可出现被调用方术语。
- 目标模块 API 只公开自身稳定业务错误；GatewayImpl 是唯一可以识别目标模块错误码、异常类型并转换为调用方错误语义的类型。调用方 Handler 不得处理目标模块错误。
- 查询量大的 Insights/列表场景允许 infrastructure 实现 application 中按业务命名的投影读取接口并返回只读投影，不强制先还原聚合。

## 3. 允许依赖矩阵

箭头表示左侧允许同步依赖右侧公开 Named Interface。事件消费不形成发布者对消费者的反向依赖。

| 模块 | 允许同步依赖 |
| --- | --- |
| `IdentityAccess` | `sharedkernel`, `platform` |
| `ProductCatalog` | `sharedkernel`, `platform` |
| `Portfolio` | `IdentityAccess::api`, `ProductCatalog::api`, `sharedkernel`, `platform` |
| `MarketData` | `IdentityAccess::api`, `ProductCatalog::api`, `sharedkernel`, `platform` |
| `Accounting` | `IdentityAccess::api`, `Portfolio::api`, `ProductCatalog::api`, `MarketData::api`, `sharedkernel`, `platform` |
| `Discipline` | `IdentityAccess::api`, `Portfolio::api`, `ProductCatalog::api`, `MarketData::api`, `Accounting::api`, `sharedkernel`, `platform` |
| `InvestmentPlan` | `IdentityAccess::api`, `Portfolio::api`, `MarketData::api`, `Accounting::api`, `sharedkernel`, `platform` |
| `Insights` | 所有核心模块的只读 API，以及 `sharedkernel`, `platform` |
| `Importing` | `IdentityAccess::api`, `ProductCatalog::api`, `Portfolio::api`, `Accounting::api`, `sharedkernel`, `platform` |
| `platform` | 无业务模块依赖 |
| `sharedkernel` | 无业务模块依赖 |

关键解环规则：

- `Portfolio` 不同步调用 Accounting/Discipline/InvestmentPlan。作废先改变 `PortfolioFund` 有效性并发布事件；下游所有计算通过 `Portfolio::api` 的有效性契约即时过滤，事件处理器再维护各自的失效投影。
- `Accounting` 不依赖 `Discipline` 或 `InvestmentPlan`。后两者发命令给 Accounting，Accounting 通过事件反馈确认或撤销结果。
- `MarketData` 不依赖 Accounting。`NavPublished` 事件由 Accounting 消费。
- 核心模块均不得依赖 `Insights` 或 `Importing`。

## 4. 模块公开 API

公开类型名称是设计占位，实施时可按现有命名微调，但职责和方向不得改变。

| 模块 | 按职责划分的 API | 主要聚合/对象 |
| --- | --- | --- |
| IdentityAccess | `AuthenticationApi`, `CurrentActorApi`, `UserAdministrationApi` | `User`, `Session`, `Role` |
| ProductCatalog | `FundProductApi`, `FundFeeApi` | `FundProduct`, `FeeSchedule` |
| Portfolio | `PortfolioFundApi`, `FundGroupApi` | `PortfolioFund`, `FundGroup` |
| Accounting | `TransactionApi`, `PositionApi` | `Transaction`, `Lot`, `Position` |
| MarketData | `FundMarketDataApi`, `MarketCalendarApi`, `WatchedIndexApi` | `NavSeries`, `MarketSnapshot`, `Watchlist` |
| Discipline | `DisciplineApi`, `AdviceApi` | `DisciplinePolicy`, `TakeProfitCycle`, `Advice` |
| InvestmentPlan | `InvestmentPlanApi`, `DcaBudgetApi` | `InvestmentPlan`, `MonthlyBudget` |
| Insights | `FundInsightApi`, `PortfolioInsightApi` | 查询投影 |
| Importing | `YangjibaoImportApi` | `ImportSession`, `ImportItem` |

模块 API 只按业务职责拆分，不因方法是命令还是查询而拆分。例如 `PortfolioFundApi` 同时负责跟踪、更新、作废、查询详情和校验有效性；`FundGroupApi` 单独负责分组。禁止建立模块级万能 API，也禁止仅以技术操作类型命名 `CommandApi`、`QueryApi`。跨模块标识建议在 `sharedkernel` 中仅保留 `UserId`、`FundProductId`、`PortfolioFundId` 三个稳定值对象。不得把现有 `FundEntity` 或任何模块 DTO 放入 shared kernel。

## 5. 数据所有权

### 5.1 目标所有者映射

| 当前表/结构 | 目标模块 | 迁移说明 |
| --- | --- | --- |
| `site_user`、会话存储 | IdentityAccess | 保留表名可先只迁包和契约 |
| `fund_dict`, `fund_fee` | ProductCatalog | 与用户基金关系解耦 |
| `fund` | ProductCatalog + Portfolio + Accounting | 拆为产品、组合关系和 Position；过渡读模型只服务数据对账，不承诺旧接口兼容 |
| `fund_group`, `fund_group_member` | Portfolio | 关联键最终指向 `portfolio_fund` |
| `fund_transaction`, `fund_lot`, `fund_lot_redemption` | Accounting | 引用 `portfolio_fund_id`，保留来源引用和净值快照 |
| `fund_nav_history` | MarketData | 只按 `fund_product_id/code` 归属，不再关联用户基金 |
| `market_indicator_snapshot`, `index_kline`, `trading_calendar` | MarketData | 保持全局行情事实 |
| `fund_strategy`, `fund_strategy_activation`, `signal_log` | Discipline | 引用 `portfolio_fund_id` |
| `fund_dca_plan` | InvestmentPlan | 引用 `portfolio_fund_id` |
| `portfolio_return_snapshot` | Insights | 保留用户级只读投影语义 |
| `user_config.monthly_dca_budget` | InvestmentPlan | 迁入模块自有设置表 |
| `user_config.watched_indices` | MarketData | 迁入结构化关注列表；不再以逗号字符串作为领域模型 |
| 养基宝 Redis 会话/外部 DTO | Importing | 外部模型不得进入核心模块 |

### 5.2 Fund 拆分

目标模型：

```text
FundProduct 1 ─── * PortfolioFund 1 ─── 1 Position
                        │
                        ├── * Transaction/Lot
                        ├── * DisciplinePolicy/Advice
                        └── * InvestmentPlan
```

- `FundProduct` 全局按基金代码唯一。
- `PortfolioFund` 至少包含 `id`, `ownerId`, `fundProductId`, `validity`, 提醒配置和审计字段；同一用户与产品的重复观察规则沿用当前产品行为，并在数据分析后决定唯一约束。
- `Position` 是 Accounting 查询模型，由账本重算；迁移初期可保留快照字段，但账本始终是事实来源。
- `openedAt` 和 `costPerShare` 从当前 `fund` 迁入 Accounting；纪律分类从当前 `fund.fund_category` 迁入 Discipline；产品类型和跟踪指数迁入 ProductCatalog。

## 6. 状态与关键流程

### 6.1 PortfolioFund 状态

```text
TRACKED ──void(reason)──> VOIDED
```

`TRACKED` 与 Position 状态正交。当前持仓、观察池、清仓历史由 Position 推导，不再写入 PortfolioFund 状态。`VOIDED` 是不可恢复终态，系统不提供 `restore` 命令或恢复事件；误作废时重新建立新的 `PortfolioFund`，原记录继续保留审计。

### 6.2 Position 状态

```text
无 CONFIRMED 交易 -> EMPTY
CONFIRMED 净份额 > 0 -> OPEN
已有 CONFIRMED 交易且净份额 <= 0 -> CLEARED
CLEARED 后确认正向交易 -> OPEN
```

当前列表筛选 `PortfolioFund=TRACKED && Position in (EMPTY, OPEN)`；历史列表筛选 `PortfolioFund=TRACKED && Position=CLEARED`。累计收益查询包含 TRACKED 的 OPEN 与 CLEARED 历史，排除 VOIDED。

### 6.3 作废

1. Web 适配器校验当前操作者、要求原因和二次确认。
2. Portfolio 在事务内将 `PortfolioFund` 标记 VOIDED，记录 `voidedAt/voidedBy/reason`，发布 `PortfolioFundVoided`。
3. 所有计算入口在读取时通过 `PortfolioFundValidity` 排除 VOIDED，因此 HTTP 返回后立即不再参与业务计算。
4. Accounting 事件处理器幂等标记关联账目为“因组合项作废而失效”，保留原状态和值；PENDING 不再进入确认扫描。
5. Discipline 和 InvestmentPlan 幂等停用关联配置；MarketData 仅移除该用户关系带来的关注需求，不删除全局净值；Insights 删除或重建对应投影。
6. Event Publication Registry 记录未完成消费者，后台重试；监控暴露积压和最老失败时间。

### 6.4 清仓与再入场

1. Accounting 确认卖出/调减，重算净份额为零并发布 `PositionCleared`/`TransactionConfirmed`。
2. Insights 将其从当前列表移入已清仓历史，但累计收益仍使用全部有效历史账目。
3. Discipline 停止对无持仓对象生成卖出建议，但保留历史建议。
4. 后续正向交易确认后 Accounting 发布 `PositionOpened`，Insights 重新放入当前列表，成本按现有清仓再入场规则重建。

### 6.5 初始持仓

基金开户由 Accounting 的 `OnboardPortfolioFund` 应用用例编排：先调用 Portfolio 的 `TrackPortfolioFund` 创建组合关系；若请求携带初始份额，再由 Accounting 在同一本地事务记录期初账目。Accounting 使用 MarketData 最近已公布单位净值和用户输入成本创建同步 CONFIRMED 的初始账目与 lot。任一步失败则组合关系创建回滚；不带初始份额时该用例只完成观察关系创建。行情补充仍可独立提交并复用，保持 ADR-0012 现有业务语义。该方向符合 `Accounting -> Portfolio`，Portfolio 不反向依赖 Accounting。

### 6.6 净值确认

MarketData 保存新净值并发布 `NavPublished(fundProductId, navDate, nav, publicationId)`。Accounting 监听后按产品查找 PENDING 交易，在每个 PortfolioFund 独立事务中确认，并发布 `TransactionConfirmed`。确认交易固化 `navDate/nav/source` 快照，不能引用可变行情实体。

### 6.7 建议回应

Discipline 接收用户回应并记录状态；接受建议时同步调用 Accounting 创建 PENDING 交易，并保存 `adviceId` 业务幂等键。交易确认/撤销后，Discipline 通过事件推进建议生命周期。Accounting 不反向调用 Discipline。

### 6.8 计划执行

InvestmentPlan 使用 MarketData 的交易日历判断执行日，以 `planId + businessDate` 调 Accounting 创建 `INVEST/PENDING`。Accounting 的唯一约束保证重试不重复；确认或撤销事件更新计划执行视图，但不修改计划规则。

## 7. 跨模块集成事件目录

| 事件 | 发布者 | 主要消费者 | 关键性/幂等键 |
| --- | --- | --- | --- |
| `PortfolioFundTracked` | Portfolio | MarketData, Discipline, Insights | 关键；`portfolioFundId` |
| `PortfolioFundVoided` | Portfolio | Accounting, Discipline, InvestmentPlan, MarketData, Insights | 关键；`portfolioFundId + voidedAt` |
| `NavPublished` | MarketData | Accounting, Insights | 关键；`fundProductId + navDate` |
| `TransactionCreated` | Accounting | Insights | 可重建；`transactionId` |
| `TransactionConfirmed` | Accounting | Discipline, InvestmentPlan, Insights | 关键；`transactionId + version` |
| `TransactionCancelled` | Accounting | Discipline, InvestmentPlan, Insights | 关键；`transactionId + version` |
| `PositionOpened` | Accounting | Discipline, Insights | 关键；`portfolioFundId + positionVersion` |
| `PositionCleared` | Accounting | Discipline, Insights | 关键；`portfolioFundId + positionVersion` |
| `AdviceResponded` | Discipline | Insights | 可重建；`adviceId + responseVersion` |
| `PlanExecuted` | InvestmentPlan | Insights | 可重建；`planId + businessDate` |

下表事件均属于发布模块的 `application.event`，由 application 根据内部领域事件或应用用例结果生成。事件统一包含 `eventId`, `occurredAt`, `aggregateId`, `aggregateVersion` 和必要的业务快照；不包含 Domain Entity 或 JPA Entity。消费者建立自己的去重表或唯一键，不能只依赖“正常情况下只投递一次”。

每个消费者在自身 `adapter.event.<business-capability>` 中监听公开集成事件，转换为本模块 Command 后委派 Handler；监听器不得直接访问对方 Domain、Repository 或消息持久化实现。

## 8. REST 重设计与前端切换策略

| 当前路径职责 | 目标适配器所有者 |
| --- | --- |
| `GET /api/funds`, `GET /api/funds/{id}` | Insights |
| `POST /api/funds` | Accounting 的开户适配器编排 Portfolio；无初始份额时仅创建观察关系 |
| `PUT/DELETE /api/funds/{id}` | Portfolio |
| `GET /api/funds/search`, `GET .../fee-rates` | ProductCatalog |
| `/api/funds/{id}/transactions`, `/api/transactions/**` | Accounting |
| `/api/funds/{id}/market-*`, `/api/market/**` | MarketData |
| `/api/funds/{id}/strategies`, `/signals`, `/operations` | Discipline |
| `/api/dca-plans`, `/api/dca/**` | InvestmentPlan |
| `/api/portfolio/**` | Insights |
| `/api/user-config` | 拆为 InvestmentPlan 与 MarketData 各自的新职责接口，前端迁移后废弃旧入口 |
| `/api/imports/yangjibao/**` | Importing |
| `/api/admin/**` | 所属模块的 `adapter.web.<business-capability>`；管理 Controller 与对应普通入口按同一业务职责归包 |

表中路径只用于识别当前功能，不约束新 URL。每个切片先定义目标 Web Adapter 的资源与用例语义，再同步修改前端调用；使用 MockMvc 验证新接口的权限、状态码和业务结果，不对旧路径、旧错误码或旧 JSON 建立兼容断言。

## 9. Spring Modulith 集成

- 依赖基线：Spring Modulith `2.0.7`，与当前 Boot 4.0 系列配套；不采用面向 Boot 4.1 的 Modulith 2.1。
- 迁移期使用显式模块检测，只对已添加 `@ApplicationModule` 的新包执行边界验证；legacy 包清单必须在测试中显式列出且只减不增。
- 最终移除 legacy 清单并恢复直接子包自动检测，`ApplicationModules.of(FundPilotBackendApplication.class).verify()` 验证无循环、只访问公开 `adapter.api`/事件且符合 allowed dependencies。
- 每个写模块至少有一个 `@ApplicationModuleTest`；跨模块事件用 `Scenario` 验证发布与消费。
- 生产事件发布记录由 Flyway 创建，Hibernate 保持 `ddl-auto=validate`。精确 DDL 使用所选 Modulith 2.0.7 持久化实现发布的 PostgreSQL schema，不手写猜测列定义。
- 事件完成记录采用保留期清理；失败积压、重试次数、最老未完成发布时间进入 Actuator/Micrometer 指标与告警。

官方依据：Spring Modulith Fundamentals、Application Modules、Runtime Support 和 Moments/Event Publication Registry 文档，以及 Maven Central 对 2.0.7 的 Boot 4.0.x 依赖元数据。实施前应再次核验当前官方 2.0.x 最新补丁版本，但升级补丁仍需用户批准。

## 10. 安全与事务

- Web Adapter 统一从 `CurrentActor` 解析 `actor`，普通入口不接受客户端 ownerId；Handler 与模块 API 不得直接读取 HTTP 或 `SecurityContext`。
- 管理 Web Adapter 先校验 ADMIN，再显式接收目标 userId 作为 `subject`；写命令和模块 API 请求显式携带 actor 与 subject，Handler 执行授权和业务校验并审计两者。
- Scheduler 与 Event Adapter 使用明确、可审计的系统执行者构造命令；不得伪造普通用户身份或依赖线程上下文中的 HTTP 认证状态。
- 单体数据库内，同步 `Gateway -> GatewayImpl -> adapter.api -> Handler` 调用链必须加入调用方的同一本地事务，任一步失败整体回滚；该链路不得使用 `REQUIRES_NEW`。任何远程数据拉取都不得占用长事务。
- 事件只在源事务提交成功后对异步消费者可见；每个消费者各自开启独立事务并实现幂等。
- 作废、初始持仓、交易确认、建议回应和计划执行都必须有并发/重复请求测试。

## 11. 迁移与回滚策略

- 每个切片先扩展 schema/契约，再回填和双读校验，最后切流；旧字段删除必须延后到至少一个稳定版本之后。
- 应用回滚只回到仍兼容扩展 schema 的版本；Flyway 不做 down migration，错误数据通过修复迁移前滚。
- 迁移任务记录行数、校验和、孤儿引用、用户级汇总差异及运行耗时；生产执行前在数据库副本演练并备份。
- 每个模块切换后运行全量测试、Modulith verify、新 API 行为测试和模块所有权 SQL 对账。
- 任何阶段出现累计收益、持仓份额、交易状态、用户隔离差异时停止后续切片并回切读路径。

## 12. 主要风险

| 风险 | 控制措施 |
| --- | --- |
| 将 DDD 变成目录重命名 | 先定义数据所有权、契约和不变量，再搬文件 |
| Fund 拆表导致历史收益漂移 | 账本对账、逐用户汇总差异为零、双读影子比较 |
| 作废事件尚未消费造成短暂污染 | 所有计算先同步检查 Portfolio 有效性，事件只做本模块投影和清理 |
| 事件重复或失败 | 业务幂等键、Event Publication Registry、积压监控和重放测试 |
| legacy 例外长期不收敛 | 例外清单只减不增，并在每个切片验收中统计 |
| REST 与前端切换遗漏功能 | 按用例清单联调新接口，并用 MockMvc 验证权限、状态码和业务结果 |
| 大量表同时迁移无法回滚 | 纵向切片、expand/migrate/contract、延后删除旧结构 |

## 13. 已确认决策

- 作废不可恢复。作废表示“整段记录从未有效”的审计结论；误作废时重新建立正确的 PortfolioFund，不跨 Accounting、Discipline、InvestmentPlan 恢复旧历史状态。底层记录永久保留审计。
- 跨模块能力固定命名为 `application.gateway` / `infrastructure.gateway`。前者按当前模块业务语言定义 Gateway，后者以 GatewayImpl 形式调用目标模块 `adapter.api` 并完成模型转换；其他包禁止直接引用外部模块 API。
- 本模块 Web/Scheduler/Event Adapter 直接调用同模块按职责划分的 Command/Query Handler；其他上下文必须经过调用方 `application.gateway`、`infrastructure.gateway` GatewayImpl 和目标模块 `adapter.api` 入站 Adapter。Adapter 先按协议、再按业务职责分包，管理入口不形成统一 `admin` 业务包。
