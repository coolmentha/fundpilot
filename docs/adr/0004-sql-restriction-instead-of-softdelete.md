# 软删除机制从 @SoftDelete 改用 @SQLRestriction + @SQLDelete

`AbstractEntity` 的软删除实现从 Hibernate 7 的 `@SoftDelete` 注解,改为 `@SQLRestriction("deleted_date IS NULL")` + `@SQLDelete(sql = "UPDATE ... SET deleted_date = now() WHERE id = ?")`。

## 背景

`推荐架构方案.md §0` 与初版 `AbstractEntity`(commit `9403fed`)采用 `@SoftDelete(columnName = "deleted_date", strategy = SoftDeleteType.TIMESTAMP)`。本期 issue #2 落地时首次真正启动 ApplicationContext(Hibernate validate),发现 `@SoftDelete` 与 `@ManyToOne(fetch = LAZY)` 冲突。

## Considered Options

- **A. 保留 @SoftDelete,所有关联改 EAGER**。语义完整,但 FundPilot 几乎所有实体被 LAZY 关联(FundTransaction→Fund、SignalLog→Fund、FundStrategy→Fund…),全 EAGER 会让仓位聚合等查询 N+1 爆炸,生产不可用。
- **B. 改用 @SQLRestriction("deleted_date IS NULL") + @SQLDelete(已采纳)**。`@SQLRestriction` 让 Hibernate 自动给所有查询追加 `WHERE deleted_date IS NULL` 过滤,`@SQLDelete` 把 `remove()` 重定向为 `UPDATE ... SET deleted_date = now()`。软删语义与 §0「归档=软删除,与持仓状态正交」完全一致,且不触发 LAZY 限制。
- **C. 去掉软删机制,归档直接物理删**。偏离 CONTEXT.md §硬性原则「归档=软删除」,且无法满足「任意 FundStatus 可归档,关联数据一起隐藏」。

## Consequences

选 B 的核心理由:`@SoftDelete` + `LAZY` 关联在 Hibernate 7 是**硬限制**,不是"未实现的特性"——

1. 源码硬编码: `ToOneAttributeMapping` 在元模型构建阶段 `if (entityMappingType.getSoftDeleteMapping() != null && getTiming() == FetchTiming.DELAYED) throw UnsupportedMappingException(...)`(`DELAYED` 即 LAZY)。关联实体只要带 `@SoftDelete`,直接拒绝构建。
2. 官方测试当契约: `SoftDeleteFetchModeTests` 用 `catch (UnsupportedMappingException expected)` 把这个异常当预期行为断言,不是待修 bug。
3. 无未来计划: GitHub issue tracker 搜 "SoftDelete lazy" 零结果,`@SoftDelete` Javadoc 不提此限制,团队视为有意识的设计选择。

`@SQLRestriction` 是普通 SQL 过滤,不依赖 Hibernate 软删特技,长期稳定,不会被 Hibernate 升级打破。

偏离《推荐架构方案.md §0》的具体表述「`AbstractEntity` 现有的 `@SoftDelete` + `deletedDate`」——本 ADR 取代该表述,§0 文档应同步修订为 `@SQLRestriction + @SQLDelete`。

关于 `@NotFound`:`@SQLRestriction` 方案下,若关联父行被软删,LAZY 加载会返 null(查询被过滤掉)。PRD §0 保证「归档时关联数据一起软删」,正常不会出现父子不同步。一旦出现说明软删逻辑有 bug,默认行为(null 而非抛异常)利于容错;如需严格护栏,后续可在具体关联上加 `@NotFound(action = EXCEPTION)`。

## 关联

- 取代《推荐架构方案.md §0》关于软删除机制的具体表述。
- issue #2 (coolmentha/fundpilot#2) 落地时发现并修复。
