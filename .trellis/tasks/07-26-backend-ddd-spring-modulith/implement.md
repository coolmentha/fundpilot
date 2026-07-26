# 实施计划：后端 DDD 与 Spring Modulith

## 前置门禁

- [x] 用户批准最终 `prd.md`、`design.md` 和本实施计划。
- [x] 用户已确认作废不可恢复；误作废时新建 PortfolioFund，旧记录仅保留审计。
- [x] 用户授权创建并切换 `feature/backend-ddd-spring-modulith`；分支必须从最新 `main` 创建。
- [x] 用户单独批准新增 Spring Modulith 2.0.x 依赖。
- [x] 涉及事件发布表或业务拆表前，再次说明 schema、生产备份、回滚和验证并取得确认。用户已批准 V40（Accounting 拆表）、V41（Discipline）、V42（InvestmentPlan）、V43（Modulith 事件表）；均为 expand-only 且保留旧列。Slice 12 只做非破坏性部分，删除旧列旧表另行获批。
- [x] 开始每个切片前运行 `trellis-before-dev`，读取 `.trellis/spec/backend/index.md` 及其要求的规范。

## 总体执行方式

按纵向切片迁移，每个切片完成“领域模型/应用用例/持久化适配/新 REST 接口与前端切换/测试/架构门禁”后才进入下一个。禁止一次移动全部现有文件，禁止把目录重命名、依赖升级、schema 拆分和业务行为修改混在同一提交中。

## Slice 0：领域契约与迁移基线

- [x] 在功能分支更新 `CONTEXT.md`：定义 FundProduct、PortfolioFund、Position、作废、清仓、纪律分类、产品类型、建议回应。
- [x] 更新 `docs/business/` 中“归档”的旧语义，并新增作废/清仓验收场景。
- [x] 创建 ADR：选择模块化单体、边界、同步调用与持久化事件的取舍；记录为何不拆微服务、不一次性重写。
- [x] 生成当前包依赖、Controller 路径、表/实体所有权和业务错误码基线清单。
- [x] 记录当前 REST 路径职责与权限边界，仅作为新接口设计与前端迁移盘点，不建立兼容性门禁。
- [x] 运行现有全量测试并保存基线结果；当前环境因 PostgreSQL `localhost:5432` 未启动而无法通过集成测试。

停止点：本切片仅文档和回归基线，可直接撤销分支改动，不触及生产数据。

## Slice 1：引入 Spring Modulith 架构门禁

- [x] 在 `pom.xml` 以 BOM 管理方式加入用户批准的 Spring Modulith 2.0.x core/test 依赖；暂不启用事件持久化。
- [x] 创建目标模块根包及 `package-info.java`，使用显式模块检测和 `allowedDependencies`。
- [x] 为每个模块建立 `adapter/application/domain/infrastructure` 四个一级层包；在 `adapter.api` 和 `application.event` 创建最小 Named Interface，不得增加第五个一级层或放置占位业务实现。
- [x] Adapter 先按 `api/web/scheduler/event` 等入口协议分包，再在协议包内按业务职责分包；普通入口与管理入口归入同一业务职责包，不建立统一 `adapter.web.admin`。
- [x] 模块 API 按业务职责划分并允许同一 API 包含读写方法；公开 Adapter 与不可变请求/结果类型放在 `adapter.api.<business-capability>`，直接委派 Handler。增加命名检查，禁止模块级万能 API 和纯技术型 `CommandApi`/`QueryApi`。
- [x] Application 内部建立 `command/query` 读写分离；按业务职责聚合为 `XxxCommandHandler` 和 `XxxQueryHandler`，不按每个用例机械拆类。本模块各类入站 Adapter 直接调用对应 Handler，不承载业务逻辑。
- [x] Application 的 `command/query/gateway/event` 固定采用“功能类别 -> 业务职责”两级分包，禁止在功能类别根包直接堆放跨职责类型。
- [x] 不建立 `port` 技术分包，禁止 `XxxPort` 命名；跨模块和外部能力使用按业务语义命名的 Gateway/GatewayImpl。
- [x] 增加层级架构测试：adapter 只能引用本模块 application，并且不得引用 domain、Repository 或 infrastructure；application 可调用 domain，domain 不依赖其他层，infrastructure 只实现聚合职责包内的 Repository/application Gateway。
- [x] 增加入口调用约束测试：本模块 `adapter.api/web/scheduler/event` 只能调用同模块 Application Handler，不得访问 Domain、Repository 或 Infrastructure。
- [x] 增加跨模块调用测试：Application 不得引用其他上下文；只有调用方 `infrastructure.gateway` 中的 GatewayImpl 可以引用目标模块公开 `adapter.api`，Gateway/GatewayImpl 必须使用调用方业务语义命名。
- [x] 增加 Gateway 签名测试：调用方 `application.gateway` 不得引用目标模块 `adapter.api` 的 DTO；仅 GatewayImpl 可调用目标 API 并承担请求/结果转换。
- [x] 增加跨模块错误映射测试：目标 API 仅暴露稳定业务错误；只有 GatewayImpl 可识别并转换目标模块错误，调用方 Handler 不得引用目标错误码或异常类型。
- [x] 增加 Gateway 命名检查：Gateway/GatewayImpl 的包名和类型名按调用方业务职责命名，不得使用目标模块聚合、API 或 DTO 名称。
- [x] 增加同步调用事务测试：Gateway/GatewayImpl/目标 API/目标 Handler 共享同一本地事务，任一步异常整体回滚；该链路不得使用 `REQUIRES_NEW`。事件消费者必须在源事务提交后以独立事务执行。
- [x] 增加 Domain 纯净性测试，禁止 `domain..` 引用 JPA、Spring、Spring Modulith、Jackson、Lombok、模块 adapter/application/infrastructure。
- [x] Domain 按聚合职责分包，禁止建立统一 `domain.model/repository/service/event` 技术包；聚合根、值对象、Repository 接口和内部事件必须归入所属聚合包。
- [x] 增加限界上下文测试：任何 `domain..` 不得引用其他顶层业务模块；跨上下文同步调用只能由 `infrastructure.gateway` 引用对方公开 `adapter.api`，异步协作只引用公开事件契约。
- [x] 增加 `platform/sharedkernel` 依赖门禁：二者不适用四层目录且不得依赖业务模块；`sharedkernel` 仅允许无框架稳定 ID、值对象和共享规则，且不得依赖 `platform`。
- [x] 建立事件转换约定：聚合包内的领域事件仅模块内部使用；发布方 Handler 生成 `application.event` 集成事件，由 `infrastructure.messaging` 持久化发布；订阅方只在 `adapter.event` 监听并委派本模块 Handler。
- [x] 增加事件层级测试：Application 不得监听其他模块事件，Adapter 不得直接调用消息基础设施；监听器不得引用对方 Domain、Repository 或内部 DTO。
- [x] 建立持久化映射约定：`infrastructure.persistence` 按聚合职责分包；JPA Entity、Spring Data、Repository 实现和 Domain Mapper 共同放入对应聚合包，禁止统一 `entity/repository/mapper` 技术分包。
- [x] Infrastructure 固定采用“技术能力 -> 职责”两级分包：`persistence/<aggregate>`、`gateway/<calling-capability>`、`remote/<external-capability>`、`cache/<business-capability>`、`messaging/<publishing-capability>`；禁止 `gateway.impl`、`remote.client` 等跨职责技术大包。
- [x] 新增 `ApplicationModules.verify()` 架构测试。
- [x] 新增 legacy 包例外清单测试，并禁止新模块引用 legacy 内部类型；例外数量写入测试输出。
- [x] 验证应用启动、模块文档模型和全量测试。622 tests / 0 failures / 0 errors，Modulith Documenter 可生成全部模块画布。

停止点：移除 Modulith 依赖、包声明和架构测试即可回滚，无 schema 变化。

## Slice 2：IdentityAccess 与权限边界

- [x] 将 `site_user`、认证会话、角色和当前操作者迁入 IdentityAccess。
- [x] 暴露 `CurrentActor`、管理用户命令和最小用户查询 API。
- [x] 把认证和管理员用户 Controller 按认证、用户管理等业务职责迁入 `identityaccess.adapter.web.<business-capability>`，保持 URL 不变，不建立统一 admin 包。
- [x] 补充“管理员普通接口只看自身、管理接口显式目标用户”的集成测试。
- [x] 增加身份上下文测试：Web Adapter 解析 actor/subject 并显式传给命令/API；Handler/API 不读取 HTTP 或 `SecurityContext`；Scheduler/Event Adapter 使用可审计的系统执行者。
- [x] 删除业务模块对 `admin`/`user` 内部类的引用。

停止点：表名不变；旧 Controller 可在切流前恢复，schema 无破坏性变化。

## Slice 3：ProductCatalog 与全局 FundProduct

- [x] 迁移基金字典、产品搜索、费率和产品资料外部适配器。
- [x] 新增 `FundProduct` 持久化结构或从现有数据生成全局产品表，按基金代码回填并校验冲突。
- [x] 暴露产品查询、费率查询和默认纪律分类建议 API。
- [x] 为产品搜索、费率和管理同步设计新接口，并同步迁移前端调用。
- [x] 对同代码多用户数据执行名称、类型、指数字段冲突报告；不得静默选择冲突值。

停止点：保留 `fund` 原字段和双读校验；未切写前可回滚应用。

## Slice 4：Portfolio 与 PortfolioFund

- [x] 新增或演进 `portfolio_fund`，回填 owner/product 关系、分组和仓位提醒。
- [x] 定义 `TRACKED -> VOIDED` 单向状态机与审计字段，不实现 restore 命令或恢复事件。
- [x] 迁移观察、更新、分组、提醒和作废用例；删除跨模块级联软删。
- [x] 发布 `PortfolioFundTracked`/`PortfolioFundVoided`，为消费者预留幂等处理。
- [x] 在所有尚未迁移的计算入口加入统一有效性过滤，确保 VOIDED 立即排除。
- [x] 按 Portfolio 语言设计跟踪、更新和作废接口；创建含初始持仓的完整编排在 Accounting 切片落地，前端不继续依赖 legacy 契约。

验证重点：错误代码/初始持仓作废、存在 PENDING、已清仓后作废、跨用户拒绝、重复作废幂等、审计字段完整。

停止点：保留旧 `fund` 行和映射；读写开关可回切 legacy，禁止删除旧数据。

## Slice 5：MarketData 所有权

- [x] 将净值历史、指标、K 线、交易日历和实时缓存迁入 MarketData 包。
- [x] 将行情关联从用户 `fund_id` 转为 `fundProductId/fundCode`，校验同代码数据唯一性和最新日期。
- [x] 拆分统一 `MarketDataSource` 为产品资料、净值、指数行情等按业务能力命名的窄接口。
- [x] 通过职责 API 暴露净值、交易日历和实时行情能力。
- [x] 发布不可变 `NavPublished` 事件；暂以同步/现有监听方式验证业务等价。
- [x] 拆分 `user_config.watched_indices` 到 MarketData 自有结构，完成数据回填与前端新接口切换后移除旧入口。

停止点：保留旧外键/字段和双读差异日志；行情表迁移失败不得继续 Accounting 切片。

## Slice 6：Accounting 与 Position

> 进行中。V40 expand migration 已落地并验证（`fund_transaction`/`fund_lot` 新增 nullable `portfolio_fund_id` 并回填、新增 `accounting_position` 并从 `fund` 回填对账）。写入侧尚未切流：两处新列/新表目前无代码写入，收紧 `NOT NULL` 与删除 legacy 账目实现属于本切片剩余工作。

- [ ] 迁移 transaction、lot、redemption、费用计算、确认/撤销和持仓重算。
- [ ] 交易改为引用 `PortfolioFundId`；固化确认所用单位净值快照与来源。
- [ ] 将 `openedAt/costPerShare/status` 从 `fund` 迁入 Accounting 的 Position 模型，账本回放结果必须与现有值对账。
- [ ] 通过 ProductCatalog 费率 API 和 MarketData NAV API 替代跨 Repository 访问。
- [ ] 消费 `NavPublished`，发布交易与 Position 生命周期事件。
- [ ] 实现 `PortfolioFundVoided` 幂等处理；无论投影是否完成，查询都通过 Portfolio 有效性排除作废项。
- [ ] 由 Accounting 的开户用例在同一本地事务内调用 Portfolio 创建关系并按需记录初始持仓，提供新的领域化接口并同步迁移前端。
- [ ] 迁移初始持仓、手动交易、转换、调整和确认工作台 REST 适配器。

验证重点：逐用户/逐基金份额、在途份额、成本、已实现收益、费用、lot 余额、交易状态和清仓再入场全部无差异；并发确认、重复事件和超卖测试通过。

停止点：旧字段只读保留至少一个稳定版本；对账有任何差异即回切读路径，不执行 contract migration。

## Slice 7：Discipline

- [ ] 合并 strategy/signal 为 Discipline，迁移策略、激活周期、建议日志和回应状态。
- [ ] 将 FundCategory 迁为最终纪律分类，记录默认建议来源和用户确认/自定义状态。
- [ ] 经本模块 Gateway/GatewayImpl 使用 Portfolio/MarketData/Accounting 公开 API，替代 Entity/Repository 引用。
- [ ] 接受建议时以 adviceId 幂等调用 Accounting 创建交易。
- [ ] 消费 TransactionConfirmed/Cancelled、PositionOpened/Cleared 和 PortfolioFundVoided。
- [ ] 按纪律、建议和建议回应职责设计新 REST 接口，并同步调整前端路径、DTO 与展示术语。

停止点：保留旧表和双读对比；建议生成结果按固定夹具逐项比较。

## Slice 8：InvestmentPlan 与用户配置拆分

- [ ] 迁移定投计划、状态机、调度、预算和预测。
- [ ] 使用 MarketData TradingCalendar 和 Accounting 命令；建立 `planId + businessDate` 数据库唯一幂等键。
- [ ] 消费交易确认/撤销和 PortfolioFundVoided，停用作废项计划但保留历史。
- [ ] 将 monthlyDcaBudget 从 user_config 迁入模块自有表。
- [ ] 按定投计划与组合偏好职责设计新接口，迁移前端后移除 `/api/user-config` 混合职责入口。

停止点：新旧调度器不可同时启用；使用功能开关单活切换并验证当天不会重复创建交易。

## Slice 9：Insights 查询叶子

- [ ] 将 `FundView`、FundPnl、组合汇总和 return snapshot 迁入 Insights。
- [ ] 经本模块 Gateway/GatewayImpl，通过各模块公开的职责 API 组合 FundProduct、PortfolioFund、Position、MarketData 和 Discipline。
- [ ] 实现当前列表和已清仓历史：EMPTY/OPEN 在当前视图，CLEARED 在历史视图，VOIDED 全部排除。
- [ ] 累计总收益包含有效清仓历史，逐用户与迁移前结果对账。
- [ ] 初期同步组合；仅在有性能证据时增加事件投影，不预先复制所有数据。
- [ ] 设计当前持仓、已清仓历史与组合收益的新查询接口，并同步迁移前端；无需维持现有响应结构。

停止点：可通过读路径开关恢复 legacy 查询；投影可重建，不作为唯一事实来源。

## Slice 10：Importing 防腐层

- [ ] 将养基宝会话、签名、外部 DTO 和转换迁入 Importing。
- [ ] 导入只调用 IdentityAccess、ProductCatalog、Portfolio 和 Accounting 公开契约。
- [ ] 明确预览冲突：同产品已存在、错误代码、已有持仓、重复重试和部分失败。
- [ ] 使用 importSessionId/itemId 作为幂等键，验证重试不会重复建仓或交易。
- [ ] 按导入会话职责设计新接口，并同步迁移前端。

停止点：核心模块不依赖 Importing，可独立关闭导入入口。

## Slice 11：持久化事件注册表

- [ ] 在用户批准后加入 Modulith 事件持久化实现。
- [ ] 从 2.0.x 官方 PostgreSQL schema 生成 Flyway migration，禁止运行时自动建表。
- [ ] 在 Testcontainers PostgreSQL 验证建表、发布、失败保留、重试、完成归档/清理。
- [ ] 为关键监听器增加幂等唯一键和失败注入测试。
- [ ] 增加未完成发布数量、最老事件时间、重试失败指标和运维查询说明。
- [ ] 在生产副本演练迁移；部署前备份，部署后验证事件表、Actuator 和积压为零。

停止点：先以同步业务过滤保证正确性；事件消费者可暂停并重放。Flyway 不回滚，旧应用版本必须忽略新增表。

## Slice 12：收口 legacy 与 contract migration

- [ ] 删除所有跨模块 Entity/Repository/内部 Service 引用。
- [ ] 将 legacy 例外清单缩减为零，切换为全部直接子包模块检测。
- [ ] 解散 `admin/common/exception/user/fund/market/strategy/signal/dca/portfolio/integration` 旧业务包；公共技术代码分别归 platform/sharedkernel。
- [ ] 只有在至少一个稳定版本、生产对账通过并再次获批后，删除旧列、旧表、回填桥接和双读逻辑。
- [ ] 更新 CONTEXT、业务文档、ADR、运维文档和模块图。
- [ ] 运行最终全量测试、架构验证、生产副本迁移演练和性能基线比较。

## 验证命令

以仓库届时 README/CI 的实际命令为准，预期至少执行：

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd verify
```

如 Windows wrapper 仍出现环境问题，先记录错误，再使用已安装 Maven 执行等价命令；不得把未执行说成通过。模块专项验证应包括：

- `ApplicationModules.verify()` 架构测试
- 各模块 `@ApplicationModuleTest`
- Testcontainers PostgreSQL migration 与事件重试测试
- 新 REST/认证行为测试与前端联调测试
- 数据回填校验 SQL 和逐用户收益/持仓对账
- 前端现有测试及关键基金列表、清仓历史、作废流程端到端测试

## 每个切片的完成报告

每个切片完成后必须报告：修改内容、涉及文件、schema/接口影响、验证命令与结果、数据对账结果、legacy 例外数量、剩余风险和明确回滚点。未经用户要求不 commit、不 push、不创建 PR、不部署。
