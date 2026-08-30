# AGENTS.md

工程指引索引。具体规则请按链接进入对应文档。

## Agent skills

### Issue tracker

Issues 与 PRD 通过 `gh` CLI 写入 GitHub 仓库 `coolmentha/fundpilot` 的 Issues。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认五元组：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

领域术语与核心契约由外部工作台维护，当前业务流程见 `docs/business/`，决策记录见 `docs/adr/`。详见 `docs/agents/domain.md`。

### Coding standards

backend Java 代码硬性规范（Controller 不写逻辑、@RequiredArgsConstructor、ErrorCode 枚举、Actuator、数据源降级链、全局 Instant、View DTO、魔法值枚举化）。详见 `docs/agents/coding-standards.md`。

## Git Tag 规范

- 只有 `main` 分支允许创建和推送 Git tag。
- 功能分支、修复分支及 `test` 分支禁止创建或推送 Git tag。
<!-- TRELLIS:START -->
# Trellis Instructions

These instructions are for AI assistants working in this project.

This project is managed by Trellis. The working knowledge you need lives under `.trellis/`:

- `.trellis/workflow.md` — development phases, when to create tasks, skill routing
- `.trellis/spec/` — package- and layer-scoped coding guidelines (read before writing code in a given layer)
- `.trellis/workspace/` — per-developer journals and session traces
- `.trellis/tasks/` — active and archived tasks (PRDs, research, jsonl context)

If a Trellis command is available on your platform (e.g. `/trellis:finish-work`, `/trellis:continue`), prefer it over manual steps. Not every platform exposes every command.

If you're using Codex or another agent-capable tool, additional project-scoped helpers may live in:
- `.agents/skills/` — reusable Trellis skills
- `.codex/agents/` — optional custom subagents

Managed by Trellis. Edits outside this block are preserved; edits inside may be overwritten by a future `trellis update`.

<!-- TRELLIS:END -->
