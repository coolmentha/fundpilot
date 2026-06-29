# AGENTS.md

工程指引索引。具体规则请按链接进入对应文档。

## Agent skills

### Issue tracker

Issues 与 PRD 通过 `gh` CLI 写入 GitHub 仓库 `coolmentha/fundpilot` 的 Issues。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认五元组：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

单一上下文：根目录 `CONTEXT.md` + `docs/adr/`。详见 `docs/agents/domain.md`。

### Coding standards

backend Java 代码硬性规范（Controller 不写逻辑、@RequiredArgsConstructor、ErrorCode 枚举、Actuator、数据源降级链、全局 Instant、View DTO、魔法值枚举化）。详见 `docs/agents/coding-standards.md`。
