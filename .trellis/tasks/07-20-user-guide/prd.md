# 补充网站使用手册与关键提示

## Goal

让首次使用者能在站内了解核心工作流，并在关键字段旁获得即时解释。

## Requirements

- 新增可从桌面侧栏和移动端“更多”进入的“使用帮助”页面。
- 帮助页说明首次配置、添加基金、定投、信号处理、待确认交易、行情口径和常见问题。
- 在现有页面对容易误解的术语补充 Tooltip，至少覆盖盘中估值、仓位提醒、预计份额、PENDING/待确认等场景。
- 不新增依赖，不改变业务接口和计算逻辑；Tooltip 必须有可访问名称，帮助页保持响应式。

## Acceptance Criteria

- [ ] 桌面侧栏和移动端“更多”都能打开帮助页，未知路由行为不变。
- [ ] 帮助页内容与 `docs/PRODUCT.md`、`CONTEXT.md` 的当前产品边界一致。
- [ ] 关键 Tooltip 在鼠标悬浮和键盘聚焦时可见，且不会遮挡主要操作。
- [ ] `npm run lint`、`npm test`、`npm run build` 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
