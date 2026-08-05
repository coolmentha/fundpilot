# 清理部署后未使用的 Docker 镜像

## Goal

部署成功后清理 VPS 上不再使用的 FundPilot 应用镜像，避免每次发布都持续占用磁盘。

## Requirements

- 仅在新版本完成公开健康检查并提交部署状态后执行清理。
- 仅处理 `ghcr.io/coolmentha/fundpilot-backend` 和
  `ghcr.io/coolmentha/fundpilot-frontend` 镜像，不影响 VPS 上其他项目。
- 保留当前部署正在使用的 backend/frontend 镜像；仍被容器引用的旧镜像由 Docker 拒绝删除。
- 清理失败不得让已经成功的部署回滚或失败，命令必须有时间上限并记录告警。
- 本次不删除 GHCR 历史版本，保留现有回滚所需的远端镜像来源。

## Acceptance Criteria

- [x] 部署 workflow 成功路径包含定向的旧应用镜像清理。
- [x] 清理不发生在健康检查和回滚窗口之前。
- [x] 清理命令超时或失败时部署仍保持成功，并输出告警。
- [x] YAML 中 SSH shell 脚本通过语法检查，`git diff --check` 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
