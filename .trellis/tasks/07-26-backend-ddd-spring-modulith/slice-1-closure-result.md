# Slice 1 收尾完成报告：架构门禁补齐

Slice 1 剩余的 9 项在本次全部完成，Slice 6 只完成了 expand schema 一步（见文末）。

## 修改内容

### 1. 模块 API 命名（禁止模块级万能 API）

- `productcatalog.adapter.api.product.ProductCatalogApi` → `FundProductApi`，与 design §4 的职责命名一致；同步更新全部引用（含 legacy `fund.service.FundService` 与 4 个测试）。

### 2. 事件转换链路：Handler → application.gateway → infrastructure.messaging

此前三个 Handler 直接注入 `ApplicationEventPublisher`，违反“集成事件由 `infrastructure.messaging` 投递”的约定。新增出站契约与实现：

- `portfolio.application.gateway.fundtracking.PortfolioFundEventGateway` + `portfolio.infrastructure.messaging.fundtracking.PortfolioFundEventGatewayImpl`
- `marketdata.application.gateway.navpublishing.PublishedNavEventGateway` + `marketdata.infrastructure.messaging.navpublishing.PublishedNavEventGatewayImpl`
- `marketdata.application.gateway.watchedindex.WatchedIndexEventGateway` + `marketdata.infrastructure.messaging.watchedindex.WatchedIndexEventGatewayImpl`

`PortfolioFundCommandHandler`、`NavPublishingCommandHandler`、`WatchedIndexCommandHandler` 改为依赖 Gateway；对应单元测试改用记录型测试网关，不再持有 Spring 事件发布器。这一层同时是 Slice 11 接入持久化事件注册表的唯一改动点。

### 3. 新增 `architecture/ModuleContractArchitectureTest`（8 个门禁）

| 门禁 | 约束 |
| --- | --- |
| 模块 API 命名 | 必须在 `adapter.api.<capability>` 子包；禁止 `<Module>Api` 万能 API 与 `CommandApi`/`QueryApi` |
| application 两级分包 | `command/query/gateway/event` 必须有职责子包；Handler 必须为 `XxxCommandHandler`/`XxxQueryHandler` |
| 跨模块错误映射 | 引用其他模块 `adapter.api` 的**只能**是本模块 `infrastructure.gateway` 中以 `GatewayImpl` 结尾的类型 |
| Gateway 命名 | 必须位于 `application.gateway`/`infrastructure.gateway`/`infrastructure.remote`/`infrastructure.messaging` 的职责子包；职责名必须同时存在于本模块 `application.command|query`；类名不得以目标模块名开头 |
| 同步调用事务 | `adapter.api`/`application`/`infrastructure.gateway` 上禁止 `@Transactional(REQUIRES_NEW)` |
| 事件监听器 | 只能在 `adapter.event`；必须 `@TransactionalEventListener` + `REQUIRES_NEW` 独立事务 |
| 事件发布基础设施 | 只有 `infrastructure.messaging` 可依赖 `ApplicationEventPublisher` |
| Domain 聚合分包 | `<module>.domain` 根包不得放业务类型 |

`infrastructure.remote` 被列入 Gateway 允许包，依据 design §2「外部数据源由 `infrastructure.remote` 实现」。

### 4. 模块文档模型验证

`SpringModulithStructureTest` 新增用例：用 `Documenter` 写出全部文档产物到临时目录，断言生成 `.puml` 且 9 个业务模块各有 `module-<name>.adoc` 画布。

## 验证

- `./mvnw.cmd -o clean verify`：**622 tests，0 failures，0 errors，BUILD SUCCESS**。
- 架构专项：`ModuleContractArchitectureTest`(8) + `DddLayerArchitectureTest`(4) + `SpringModulithStructureTest`(4) 全绿。
- 环境说明：集成测试依赖 `localhost:5432`，本次通过 `docker start fundpilot-test-db`（postgres:16）恢复后运行，非跳过。

## Schema / 接口影响

- Slice 1 收尾本身无 schema 变更。
- 接口影响仅限内部类型重命名 `ProductCatalogApi` → `FundProductApi`，无 REST 路径或响应变化。

## legacy 例外数量

Portfolio legacy 例外 11 个类型，门禁上限 12，与 Slice 4 持平（未新增）。

---

## Slice 6 已落地部分：V40 expand migration

`V40__add_accounting_position.sql` 已写入并验证，但 **Slice 6 未完成**，详见剩余风险。

- `fund_transaction` / `fund_lot` 新增 `portfolio_fund_id`，按 `portfolio_fund.legacy_fund_id` 回填，加 FK 与索引。
  **刻意保持 nullable**：收紧为 `NOT NULL` 属于写入侧切流，提前收紧会让当前已部署版本的 INSERT 全部失败。
- 新增 `accounting_position`（`portfolio_fund_id` 唯一、`owner_id`、`status`、`opened_at`、`cost_per_share`），从 `fund` 回填，
  `HOLDING→OPEN`、`CLEARED→CLEARED`、其余 `→EMPTY`。
- 迁移内置硬失败校验：孤儿交易/lot、行数、`portfolio_fund_id` 与 `fund_id` 一致性、position 逐字段与 `fund` 对账。
- 与批准表的差异：`fund_lot_redemption` **未**加 `portfolio_fund_id`。它通过 `lot_id`/`sell_tx_id` 关联，两者都已归属 Accounting，加列是冗余外键。
- 验证：空库 V1 → V40 迁移成功，Hibernate `validate` 通过，全量 622 tests 通过。

### 剩余风险与门禁

- `accounting_position` 目前**没有任何代码写入**，只是 `fund` 的一次性快照。在 Slice 6 写入侧切流落地前，它不是事实来源，也不得被任何查询依赖；长期不切流会与 `fund` 漂移。
- `portfolio_fund_id` 目前**没有任何代码写入**，新插入的交易/lot 该列为 NULL。同样必须由 Slice 6 切流补齐后才能收紧。
- 生产副本尚未演练 V40。部署前必须备份并完成迁移、行数与逐用户对账。

### 回滚点

- Slice 1 收尾：删除 `ModuleContractArchitectureTest`、三组 EventGateway 并还原 Handler 注入即可，无 schema 变化。
- V40：Flyway 不做 down migration。V40 为纯 expand，未删除或修改任何既有列，旧应用版本可直接忽略新列和新表继续运行。
