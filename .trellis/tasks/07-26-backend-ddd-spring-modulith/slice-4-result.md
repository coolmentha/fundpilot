# Slice 4 完成报告：Portfolio 与 PortfolioFund

## 结果

- 新增纯 Java `PortfolioFund` 聚合，状态仅允许 `TRACKED -> VOIDED`，重复作废幂等且保留首次审计。
- 新增 `FundGroup` 聚合职责包；`/api/fund-groups` 已切到 Portfolio Web Adapter，分组成员在扩展期原子双写新旧关联表。
- Accounting 负责“明确确认、原因、PENDING 交易”纠错编排，经调用方 Gateway/GatewayImpl 进入 Portfolio API。
- 删除旧基金归档 Controller/Service；作废不再跨模块级联软删交易、lot、纪律或计划。
- 前端改用 `portfolioFundId` 和作废接口，要求填写原因并确认不可恢复。

## Schema 与数据兼容

- V37 新增 `portfolio_fund` 与 `portfolio_fund_group_member`，保留 legacy `fund` 与 `fund_group_member` 作为回滚和对账来源。
- V38 将分组名称唯一性从全局改为 `owner_id + lower(name)` 的活跃行唯一索引；活跃空 owner 或用户内重复名称会阻止迁移。
- 回填 owner/product、有效性、仓位提醒、分组和历史软删审计；迁移内置缺失引用、重复有效产品、行数和关联对账硬失败。
- 作废时仅将 legacy `fund.deleted_date` 更新为有效性投影，不删除底层审计数据。
- 已验证空库 V1 -> V38、V36 夹具 -> V37、V37 -> V38、Hibernate validate，以及无 legacy ID 的新 PortfolioFund 写入。

## API 影响

- 新增 `POST /api/portfolio-funds/{portfolioFundId}/void`，请求为 `reason + confirmed`。
- `FundView` 新增 `portfolioFundId`。
- `/api/fund-groups` URL 保留，但实现已从 legacy MVC Controller 切到 Portfolio Adapter/Handler。
- 删除 `DELETE /api/funds/{id}`；不提供恢复作废记录的命令。

## 验证

- 后端：`.\mvnw.cmd clean verify`，604 tests，0 failures，0 errors，构建成功。
- 前端：`npm test -- --run`，25 files / 85 tests 全部通过。
- 前端：`npm run lint` 与 `npm run build` 成功。
- 架构：`DddLayerArchitectureTest` 与 `SpringModulithStructureTest` 通过。
- Portfolio legacy 例外：11 个类型，低于门禁上限 12；均为收益快照旧实现，后续由 Insights/Portfolio 查询切片继续迁移。
- `git diff --check` 通过。

## 数据对账

- V37 迁移验证 active -> TRACKED、legacy deleted -> VOIDED、分组成员行数一致。
- HTTP 集成验证 PENDING 阻止作废、成功作废、legacy 有效性投影、交易保留、列表排除、重复作废不覆盖首次原因。
- 分组集成验证新旧成员表同步、TRACKED 计数、删除分组仅解除关联且基金保留。

## 风险与后续门禁

- 生产数据库副本尚未执行 V37/V38 演练；部署前必须备份并完成迁移、行数、引用、分组 owner 和核心收益对账。
- `PortfolioFundTracked/Voided` 当前为事务内 Spring 事件；持久化 publication registry 与消费重试属于 Slice 11。
- legacy 收益、交易和计划模块仍通过 `fund.deleted_date` 投影过滤；各模块最终拥有失效投影将在后续切片完成。

## 回滚点

- 应用可回到读取 legacy `fund`/`fund_group_member` 的版本；V37/V38 不删除旧表列或业务数据。
- Flyway 不做向下回滚。旧应用必须忽略新增表和新索引；若新应用异常，回退应用并保留 V37/V38 数据供排查和前滚修复。
