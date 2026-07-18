# 项目质量改进

## Goal

明确仓位提醒是有意的非拦截能力，消除退役校准术语漂移，补齐核心流程测试，并降低前端首屏包体。

## Requirements

- 更新业务文档，明确仓位提醒不阻止交易；保留现有 1%-100% 提醒线契约。
- 统一策略草稿的产品文案为“草稿”，保留数据库枚举兼容，不做破坏性迁移。
- 增加覆盖登录后核心资金/持仓流程的可执行前端测试或最小集成测试。
- 对前端页面按路由/页面边界做动态加载，保持现有行为不变。

## Acceptance Criteria

- [ ] 文档与 `CONTEXT.md`、业务文档、ADR 口径一致。
- [ ] 现有 API、数据库迁移和历史枚举数据继续兼容。
- [ ] 新测试能在 CI 中运行并覆盖一条关键用户流程。
- [ ] `npm run lint`、`npm test`、`npm run build` 通过，构建包体警告明显下降或有量化说明。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
