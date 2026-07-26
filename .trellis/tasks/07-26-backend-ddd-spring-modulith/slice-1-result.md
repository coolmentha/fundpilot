# Slice 1 实施结果

记录日期：2026-07-26。

## 已完成

- 通过 BOM 引入 Spring Modulith 2.0.7 的 `starter-core` 与 `starter-test`，未引入 JDBC/JPA/Mongo 事件发布持久化模块。
- 使用 `spring.modulith.detection-strategy=explicitly-annotated`，只纳管新模块。
- 声明 9 个业务模块以及开放的 `platform`、`sharedkernel`，并配置允许同步依赖。
- 每个业务模块建立 `adapter/application/domain/infrastructure` 四层、入口协议包、Application 功能类别包和 Infrastructure 技术能力包。
- 所有业务模块公开 `api` 与 `events` Named Interface，但未创建占位业务 API、Handler 或 Domain 类型。
- 增加 Modulith verify、模块清单、Named Interface、四层依赖、Domain 纯净、跨上下文调用、技术分包和 legacy 数量门禁。

## Legacy 例外

旧 `portfolio` 包与目标模块同名，当前含 11 个源文件、12 个 ArchUnit class（包含一个内部 record）。这些类型暂留在四层之外，测试固定 class 基线不得超过 12；Slice 4 迁移后只能减少。其余旧 MVC 顶层包在显式检测策略下不属于应用模块。

## 验证

```powershell
cd backend
.\mvnw.cmd -DskipTests test-compile
.\mvnw.cmd '-Dtest=SpringModulithStructureTest,DddLayerArchitectureTest' test
.\mvnw.cmd test
```

- 编译通过。
- 架构专项测试 7 个全部通过。
- 全量测试完成编译并执行；集成测试仍因本地 PostgreSQL `localhost:5432` 未启动，在 `TestDatabaseSchema.resetOnce` 初始化隔离 schema 时失败。未观察到由 Modulith 依赖或架构测试引入的断言失败。
- 未修改 schema、认证逻辑、业务 API、业务实现或事件发布表。

## 尚待后续业务切片验证

Gateway 错误语义转换、同事务同步调用、事件消费者独立事务与幂等、真实 Command/Query Handler 分组，需要在首个实际模块迁移时用具体类型和用例测试，空骨架阶段不作完成声明。
