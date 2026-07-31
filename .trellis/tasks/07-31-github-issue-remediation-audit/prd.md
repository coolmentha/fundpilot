# GitHub Issue 修复状态审计

## Goal

审计 `coolmentha/fundpilot` 的全部 GitHub Issue，判断每项问题在当前可见代码和 GitHub 记录中是否已修复。

## Requirements

- 覆盖全部 open 与 closed Issue，不以关闭状态作为修复结论。
- 对每项记录 Issue 状态、关联 PR/提交、当前代码或测试证据。
- 将结果分为：已确认修复、未修复、关闭但证据不足、无法在本地验证。
- 不修改业务代码、部署配置或用户既有工作区改动。
- 经用户明确授权，关闭已验证修复的 GitHub Issue，并附上审计证据。
- 修复 #119：逻辑止损只接受与 Accounting 确认份额相等的全仓请求，拒绝部分卖出绕过。

## Acceptance Criteria

- [x] 全部 Issue 均已纳入审计清单。
- [x] 每个结论都有可追溯的 GitHub 或本地证据。
- [x] 明确列出仍需修复或复验的 Issue。
- [x] #119 的服务端全仓校验及正反向回归测试通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
