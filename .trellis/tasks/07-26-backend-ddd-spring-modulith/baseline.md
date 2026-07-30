# Slice 0 迁移基线

记录日期：2026-07-26。此文件描述迁移前事实，不表示目标架构已经实现。

## 技术基线

- Spring Boot 4.0.0，Java 25，Maven。
- 当前未引入 Spring Modulith；Slice 0 不修改依赖。
- 当前生产代码根包为 `com.fundpilot.backend`，按 `admin/common/config/dca/exception/fund/integration/market/metrics/portfolio/signal/strategy/user` 组织。
- 持久化使用 Spring Data JPA、PostgreSQL、Flyway；实时行情另使用 Redis。

## 当前顶层包依赖

以下边由生产源码中的 `import com.fundpilot.backend.<package>` 静态提取；同包依赖省略：

```text
admin -> common, exception, fund, market, signal, user
dca -> common, exception, fund, user
exception -> common
fund -> common, dca, exception, market, signal, strategy, user
integration -> common, exception, fund, user
market -> common, exception, fund, signal, user
portfolio -> common, fund, user
signal -> common, exception, fund, market, strategy
strategy -> common, exception, fund, market, signal
user -> admin, common, exception, fund, portfolio
```

已存在的关键环包括 `fund <-> dca`、`fund <-> market`、`fund <-> signal`、`fund <-> strategy`、`market <-> signal`、`signal <-> strategy`、`user <-> portfolio`、`admin <-> user`。迁移时必须按用例拆开，不得仅批量移动包。

## 表与目标所有权

| 当前表 | 当前实体包 | 目标模块 |
| --- | --- | --- |
| `site_user` | user | IdentityAccess |
| `user_config` | user | Portfolio |
| `fund_dict` | fund | ProductCatalog |
| `fund` | fund | Portfolio；迁移时拆出对 FundProduct 的引用 |
| `fund_group` / `fund_group_member` | fund | Portfolio |
| `fund_transaction` | fund | Accounting |
| `fund_fee` / `fund_lot` / `fund_lot_redemption` | fund | Accounting |
| `fund_nav_history` | fund | MarketData |
| `market_indicator_snapshot` / `index_kline` / `trading_calendar` | market | MarketData |
| `fund_strategy` / `fund_strategy_activation` | strategy/fund | Discipline |
| `signal_log` | signal | Discipline |
| `fund_dca_plan` | dca | InvestmentPlan |
| `portfolio_return_snapshot` | portfolio | Insights |

Importing 当前没有独立业务表；导入会话为进程内状态。共享关联表和外键在后续 schema 切片单独设计，本基线不修改 schema。

## 当前 REST 盘点

以下路径只用于盘点现有功能和前端调用，不作为兼容契约。新接口按目标领域职责重新设计，并与前端同步切换：

| 路径族 | 当前职责 | 目标入口模块 |
| --- | --- | --- |
| `/api/auth/**`, `/api/admin/users/**` | 登录、用户管理 | IdentityAccess adapter.web |
| `/api/admin/fund-dict/**` | 产品字典同步 | ProductCatalog adapter.web |
| `/api/funds/**`, `/api/fund-groups/**` | 组合基金与分组 | Portfolio adapter.web |
| `/api/transactions/**` | 交易编辑、确认、撤销 | Accounting adapter.web |
| `/api/market/**`, `/api/funds/{id}/kline`, `/intraday`, `/market-indicators/**` | 行情读取 | MarketData adapter.web |
| `/api/strategies/**`, `/api/signals/**`, `/api/funds/{id}/operations` | 纪律与建议回应 | Discipline adapter.web |
| `/api/dca-plans/**`, `/api/dca/**` | 定投计划与预算摘要 | InvestmentPlan adapter.web |
| `/api/portfolio/**` | 组合洞察 | Insights adapter.web |
| `/api/imports/yangjibao/**` | 导入会话 | Importing adapter.web |

管理员调用普通接口仍使用自己的 actor；只有 `/api/admin/**` 能显式选择其他 subject。`admin` 是入口职责，不是独立领域模块。

## 业务错误码基线

`ErrorCode` 当前包含资源不存在、输入校验、交易/信号状态、数据源、鉴权与兜底错误。它是现状盘点，不是迁移后的兼容契约；新错误按目标模块语言设计，GatewayImpl 负责将目标模块错误翻译为调用方语义。新增的作废规则预计需要独立错误码，但应随实现切片另行确认，Slice 0 不改枚举。

## 已知语义差异

- 当前 `DELETE /api/funds/{id}` 调用 `FundArchiveService`，执行旧“归档=级联软删除”；目标语义是不可恢复的组合基金作废，并保留审计证据且完全排除计算。
- 当前 `FundEntity` 同时承载市场产品、用户组合记录和部分持仓字段；目标拆为 FundProduct、PortfolioFund 与 Accounting 的 Position。
- 当前单只基金总盈亏主要表达当前未实现盈亏；目标组合累计收益必须保留清仓基金的历史已实现收益，同时排除作废记录。
- 当前 JPA 实体普遍继承带 Spring Data/JPA 注解的基类；目标 Domain 不复用该基类，持久化模型留在 Infrastructure。

## Slice 0 验证

```powershell
python .\.trellis\scripts\task.py validate 07-26-backend-ddd-spring-modulith
git diff --check
cd backend
.\mvnw.cmd test
```

结果：Trellis 校验和 `git diff --check` 通过。后端全量测试完成编译并开始执行，但集成测试在 `TestDatabaseSchema.resetOnce` 初始化隔离 schema 时因 PostgreSQL `localhost:5432` 拒绝连接而失败；这是当前运行环境基线，不是本切片文档改动导致的断言失败。恢复本地 PostgreSQL 后必须重跑并记录完整通过数量。
