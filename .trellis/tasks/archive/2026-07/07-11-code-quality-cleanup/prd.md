# 统一代码质量规范

## Goal

修复全仓库代码风格审查发现的真实行为问题，并建立可执行的前后端质量门禁，减少规范、注释和实现持续漂移。

## Requirements

- R1：非法成本单价必须返回业务错误 HTTP 400，不得进入兜底 500。
- R2：前端所有 Instant 日期时间必须按 `Asia/Shanghai` 展示，空值保持 `-`。
- R3：`DailyNavConfirmService` 的业务日期计算统一使用 `Instant` 与 `ChinaTradingDate`，`LocalDate` 仅允许留在外部数据字符串解析边界。
- R4：修正与“交易发生日净值”和显式上海时区不一致的注释。
- R5：所有有 final 依赖的 Spring `@Service`、`@Component`、`@RestController` 统一使用 `@RequiredArgsConstructor`。
- R6：前端新增 ESLint、Vitest 及 `lint`、`test` 脚本，使用现有 npm 工具链并同步 lockfile。
- R7：为时间格式化和关键前端纯函数增加单元测试。
- R8：补全前后端 Trellis quality guidelines，使其描述当前项目实际规则和验证命令。
- R9：不改变现有 API 路径、DTO 字段、数据库 schema 和业务策略。

## Acceptance Criteria

- [x] 非正成本单价返回 `BusinessException` 对应的 400 错误码。
- [x] UTC `2026-07-09T06:55:00Z` 在前端显示为北京时间 `2026-07-09 14:55:00`。
- [x] 晚间净值确认的业务日期使用北京时间自然日对应的 UTC 00:00 标签。
- [x] 审查命中的 5 个 Spring 组件不再手写构造器。
- [x] 过期的“最新净值”“服务器本地时区”注释全部修正。
- [x] `npm run lint`、`npm test`、`npm run build` 全部通过。
- [x] 后端完整测试、`git diff --check` 和 Trellis 校验通过。

## Out of Scope

- 将全部 JavaScript 迁移到 TypeScript。
- 拆分大型 CSS、Hook 或 Parser 文件。
- 引入统一 Java formatter、Checkstyle 或 SpotBugs。
