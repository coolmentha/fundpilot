# 业务需求澄清接入领域审查

## Goal

将 `grill-with-docs` 纳入 FundPilot 的业务需求澄清流程，确保方案在实现前使用领域术语、业务文档和 ADR 进行压力测试。

## Requirements

1. 从 `mattpocock/skills` 的指定路径将 `grill-with-docs` 及其 `grilling`、`domain-modeling` 依赖安装为项目本地共享技能。
2. 业务相关的需求澄清必须先确认完整技能链存在；缺失时使用环境提供的技能安装器从该固定来源安装，安装失败必须说明阻塞原因。
3. 技能可用后，先执行 `grill-with-docs` 的领域审查，再继续 `trellis-brainstorm` 的需求探索。
4. 在 `.trellis/workflow.md` 的 Request Triage、Phase 1.1、planning 状态块和平台路由中保持同一规则。

## Acceptance Criteria

- [x] 项目共享技能目录包含来源为指定 GitHub 路径的 `grill-with-docs`、`grilling` 和 `domain-modeling` 技能文件。
- [x] 工作流文本明确业务澄清的触发条件、安装来源、失败处理和与 `trellis-brainstorm` 的顺序。
- [x] 两个 planning 状态块和两组平台路由均包含该规则。
- [x] 工作流状态块标签未被破坏，相关 Trellis 命令仍可读取任务状态。

## Out of Scope

- 修改 Trellis npm 包、全局安装目录或其他项目的技能。
- 修改领域模型、业务代码或生产部署配置。
