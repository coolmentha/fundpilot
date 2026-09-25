# 表所有权清单（Table Ownership）

依据 [ADR-0022](../adr/0022-ddd-modular-monolith-boundaries.md)：后端已迁移为 Spring Modulith 约束的 DDD 模块化单体，需维护一份「表所有权清单」，把每个持久化实现归入唯一业务模块。

- **业务模块集合**（见 `backend/src/test/java/com/fundpilot/backend/architecture/SpringModulithStructureTest.java`）：`identityaccess`、`productcatalog`、`portfolio`、`accounting`、`marketdata`、`investmentplan`、`insights`、`importing`、`alerting`。另 `platform`、`sharedkernel` 为 OPEN 技术模块。**`discipline` 模块已于 v0.14.0 删除**（判定迁移为 alerting 的建议型提醒）。
- **归属判定规则**：
  1. 以 `backend/src/main/java` 中读写该表的唯一类（`@Table(name=...)` 的 JPA 实体，或 Repository/Gateway/JdbcTemplate 中 SQL 字面量所在类）所在的业务模块作为归属。
  2. `platform` 只承载技术表（框架/调度/事件注册等），不承载业务表。
  3. 在 `backend/src/main/java` 中找不到任何读写的表，标为「遗留·无代码引用」。
  4. 每行都给出可核对的证据：实体全限定类名，或 SQL 字面量所在类的全限定类名。
- **状态取值**：`在用`（当前仍由某业务模块常态读写，是归属收敛目标）/ `遗留`（已被新表取代、不再是归属收敛目标；其中部分仅剩一次性迁移/桥接代码残留引用，其余在 `backend/src/main/java` 中无任何引用）/ `已删除`（表已由迁移脚本 drop，不再存在）。

本清单用于把持久化实现收敛到唯一模块；遗留表不代表可写目标，已删除表只作历史留痕。

## 主表

| 表名 | 归属模块 | 状态 | 证据（类全限定名 或 无代码引用） |
| --- | --- | --- | --- |
| fund_nav_history | marketdata | 在用 | `com.fundpilot.backend.marketdata.infrastructure.persistence.publishednav.PublishedNavJpaEntity` |
| trading_calendar | marketdata | 在用 | `com.fundpilot.backend.marketdata.infrastructure.persistence.tradingcalendar.TradingCalendarRepositoryImpl` |
| market_indicator_snapshot | marketdata | 在用 | `com.fundpilot.backend.marketdata.infrastructure.persistence.indicator.MarketIndicatorRepositoryImpl` |
| index_kline | marketdata | 在用 | `com.fundpilot.backend.marketdata.infrastructure.persistence.indexkline.IndexKlineRepositoryImpl` |
| market_watched_index | marketdata | 在用 | `com.fundpilot.backend.marketdata.infrastructure.persistence.watchedindex.WatchedIndexRepositoryImpl` |
| index_valuation | marketdata | 在用 | `com.fundpilot.backend.marketdata.infrastructure.persistence.indexvaluation.IndexValuationRepositoryImpl` |
| fund_product | productcatalog | 在用 | `com.fundpilot.backend.productcatalog.infrastructure.persistence.product.FundProductJpaEntity`（`default_discipline_category` 保留：v0.14.0 起为收益构成的分类来源，由 `ProductCatalogSynchronizationWriter` 持续写入） |
| fund_fee | productcatalog | 在用 | `com.fundpilot.backend.productcatalog.infrastructure.persistence.fee.FundFeeScheduleJpaEntity` |
| fund_research | productcatalog | 在用 | `com.fundpilot.backend.productcatalog.infrastructure.persistence.research.FundResearchJpaEntity` |
| portfolio_fund | portfolio | 在用 | `com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund.PortfolioFundJpaEntity` |
| fund_group | portfolio | 在用 | `com.fundpilot.backend.portfolio.infrastructure.persistence.fundgroup.FundGroupRepositoryImpl` |
| portfolio_fund_group_member | portfolio | 在用 | `com.fundpilot.backend.portfolio.infrastructure.persistence.fundgroup.FundGroupRepositoryImpl` |
| fund_transaction | accounting | 在用 | `com.fundpilot.backend.accounting.infrastructure.persistence.transaction.LedgerTransactionJpaEntity` |
| accounting_position | accounting | 在用 | `com.fundpilot.backend.accounting.infrastructure.persistence.position.PositionJpaEntity` |
| fund_lot | accounting | 在用 | `com.fundpilot.backend.accounting.infrastructure.persistence.lot.LotJpaEntity` |
| fund_lot_redemption | accounting | 在用 | `com.fundpilot.backend.accounting.infrastructure.persistence.lot.LotRedemptionJpaEntity` |
| accounting_rebuild_state | accounting | 在用 | `com.fundpilot.backend.accounting.infrastructure.migration.unitnavrebuild.AccountingRebuildService` |
| discipline_strategy | — | 已删除 | v0.14.0 `V60__drop_discipline_module_and_signal_log.sql` drop，模块已删除 |
| discipline_advice | — | 已删除 | 同上 |
| discipline_classification | — | 已删除 | 同上 |
| investment_plan | investmentplan | 在用 | `com.fundpilot.backend.investmentplan.infrastructure.persistence.investmentplan.InvestmentPlanJpaEntity` |
| investment_plan_budget | investmentplan | 在用 | `com.fundpilot.backend.investmentplan.infrastructure.persistence.budget.InvestmentPlanBudgetJpaEntity` |
| investment_plan_execution | investmentplan | 在用 | `com.fundpilot.backend.investmentplan.infrastructure.persistence.execution.InvestmentPlanExecutionRepositoryImpl` |
| portfolio_return_snapshot | insights | 在用 | `com.fundpilot.backend.insights.infrastructure.persistence.portfolioreturn.PortfolioReturnSnapshotJpaEntity` |
| insights_rebuild_state | insights | 在用 | `com.fundpilot.backend.insights.infrastructure.migration.returnsnapshotrebuild.PortfolioReturnSnapshotRebuildService` |
| import_item_receipt | importing | 在用 | `com.fundpilot.backend.importing.infrastructure.persistence.importitem.ImportItemReceiptStore` |
| import_session | importing | 在用 | `com.fundpilot.backend.importing.infrastructure.gateway.importsession.JdbcImportSessionGateway` |
| site_user | identityaccess | 在用 | `com.fundpilot.backend.identityaccess.infrastructure.persistence.user.SiteUserJpaEntity` |
| alert_rule | alerting | 在用 | `com.fundpilot.backend.alerting.infrastructure.persistence.alertrule.AlertRuleJpaEntity`（v0.14.0 增列 `conditions`、`kind`、`parameters`；`rule_type`/`threshold` 转为存量审计列，不再由应用写入） |
| alert_notification | alerting | 在用 | `com.fundpilot.backend.alerting.infrastructure.persistence.notification.AlertNotificationJpaEntity`（v0.14.0 增列 `conditions_snapshot`） |
| alert_suggestion_state | alerting | 在用 | `com.fundpilot.backend.alerting.infrastructure.persistence.suggestion.SuggestionStateRepositoryImpl`（v0.14.0 新建：建议型规则的阶段、周期峰值与冷静期） |
| event_publication | platform | 在用 | `com.fundpilot.backend.platform.observability.EventPublicationMetrics` |
| scheduled_job_status | platform | 在用 | `com.fundpilot.backend.platform.observability.JobExecutionStatusStore` |
| fund | — | 遗留 | 残留引用：`com.fundpilot.backend.portfolio.infrastructure.persistence.portfoliofund.PortfolioFundRepositoryImpl`（legacy bridge，建/停用 fund 行）、`com.fundpilot.backend.accounting.infrastructure.migration.unitnavrebuild.AccountingRebuildService`（一次性重建回写 `cost_per_share`） |
| fund_strategy | — | 已删除 | 本次 `V61` drop（原「遗留」，唯一残留引用 AccountingRebuildService 的止盈状态重置已改为清空 `alert_suggestion_state`） |
| fund_group_member | — | 遗留 | 残留引用：`com.fundpilot.backend.portfolio.infrastructure.persistence.fundgroup.FundGroupRepositoryImpl`（bridge 双写） |
| fund_strategy_activation | — | 已删除 | 本次 `V61` drop（原「遗留·无代码引用」） |
| signal_log | — | 已删除 | v0.14.0 `V60` drop（原「遗留·无代码引用」，本版本连同入向外键 `fk_ft_signal_log` 一并删除） |
| strategy_backtest | — | 已删除 | 本次 `V61` drop（原「遗留·无代码引用」） |
| user_config | — | 遗留 | 无代码引用 |
| fund_dict | — | 遗留 | 无代码引用 |
| fund_dca_plan | — | 已删除 | 本次 `V61` drop（原「遗留·无代码引用」，由 `investment_plan` 取代） |
| fund_product_migration_conflict | — | 遗留 | 无代码引用 |

合计 43 张：在用 30 张，遗留 5 张，已删除 8 张（`discipline_strategy`、`discipline_advice`、`discipline_classification`、`signal_log`、`fund_strategy`、`fund_strategy_activation`、`strategy_backtest`、`fund_dca_plan`）。

## v0.14.0 已删除表

v0.14.0 整体删除 `discipline` 模块，`V60__drop_discipline_module_and_signal_log.sql` 为不可逆迁移（只删不建，执行前须备份）。

| 表名 | 原语义 | 迁移去向 |
| --- | --- | --- |
| discipline_strategy | 按基金的纪律策略参数与止盈周期状态 | 六参数与状态机迁移至 `alert_rule`（`kind=TRAILING_STOP`）与 `alert_suggestion_state` |
| discipline_advice | 每日纪律建议（含采纳状态） | 判定迁移为建议型提醒；采纳链路删除，卖出改由用户在确认页手工录入 |
| discipline_classification | 用户级纪律分类 | 分类来源改为 `fund_product.default_discipline_category`（该列保留） |
| signal_log | V1 每日信号快照 | 无后继；`fund_transaction.signal_log_id` 列与 `fk_ft_signal_log` 外键一并删除 |

## 遗留表清单

| 表名 | 语义 | 被谁取代 |
| --- | --- | --- |
| fund | V1 基金主表，混合承载产品属性与逐用户持仓态 | 由 `fund_product`（productcatalog，产品字典）、`portfolio_fund`（portfolio，追踪关系）、`accounting_position`（accounting，持仓事实）共同取代 |
| fund_strategy | V1 按基金的纪律策略参数与运行时状态（原 `user_fund_strategy`） | 由 `discipline_strategy` 取代（前身 id 经 `legacy_strategy_id` 承接，V41）；`discipline_strategy` 已于 v0.14.0 删除（见上节） |
| fund_strategy_activation | V1 基金策略激活/退役谱系 | 未能判定（V41 仅迁移 `fund_strategy` 与 `signal_log`，未迁移激活谱系） |
| strategy_backtest | V1 策略回测结果 | 未能判定（仓库内无对应新表） |
| user_config | V3 单用户账户配置（后续仅剩 `watched_indices`，再后来含 `monthly_dca_budget`） | 关注指数由 `market_watched_index`（marketdata，V39）取代；月度定投预算由 `investment_plan_budget`（investmentplan，V42）取代 |
| fund_dict | V4 东方财富基金字典本地镜像 + 识别缓存 | 由 `fund_product`（productcatalog）取代（V36 将 `fund_dict` 回填为 `fund_product`） |
| fund_dca_plan | V11 自动定投计划 | 由 `investment_plan`（investmentplan）取代，前身 id 经 `legacy_dca_plan_id` 承接（V42） |
| fund_group_member | V25 分组-基金成员关系（`fund_id` 维度） | 由 `portfolio_fund_group_member`（portfolio，`portfolio_fund_id` 维度）取代（V37 回填） |
| fund_product_migration_conflict | V36 一次性迁移冲突台账，记录全局字典与逐用户 legacy 基金行的差异待人工对账 | 未能判定（迁移期对账台账，无后继表） |