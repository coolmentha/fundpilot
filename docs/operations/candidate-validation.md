# 候选验证与正常启动

## 安全边界

候选后端只用于启动与 `/actuator/health` 检查，不承接业务流量。`validation` Profile 明确关闭 Flyway 和 Spring Modulith 未完成事件重放；调度注册、管理员初始化、账本重建、交易补偿、交易日历同步与行情缓存预热也不会在候选启动时执行。部署脚本另外使用只读容器文件系统和 PostgreSQL `default_transaction_read_only=on`，即使遗漏新的启动写入也会失败而不是落库。

候选验证要求已有 `fundpilot_default` 网络、健康的 `fundpilot-db` 与 `fundpilot-redis`，数据库 schema 必须是已确认迁移后的生产等效版本。候选模式不创建 schema、不修复 Flyway 历史，也不能替代迁移评审。

## 自动部署

`.github/workflows/deploy.yml` 在停止当前服务前，以同一后端镜像 digest 连续启动并停止候选容器两次。每次都必须通过容器内 `/actuator/health`；失败立即终止发布。脱敏后的启动配置、镜像 digest、时间、健康结果和应用日志保存在 VPS 的 `candidate-validation/<release-tag>-attempt-<n>.log`，用于核对重复启动没有调度、迁移或业务写入记录。

发布工作流先拒绝不在 `main` 上的 tag，再由 `deploy/verify-release-gate.mjs` 调用 GitHub Actions API，要求当前 tag 的完整 40 位提交 SHA 存在成功的 `ci.yml` workflow run，且同一 run 的当前 `run_attempt` 中 `Backend full test` 与 `Frontend lint, test and build` 两个 job 都成功。每次 API 请求使用 15 秒超时。`ci.yml` 只由分支 push/PR 触发且没有手动触发入口；查询再以 workflow 文件和精确 `head_sha` 限定来源，无需另造 event 白名单。run 与 job 请求均取每页 100 条；若目标 run 或规定 job 不在返回页内，会按缺失结果拒绝，不会跨页寻找宽松替代结果。结果缺失、失败或属于不同提交均在构建镜像和读取生产秘密前终止。

候选两次通过后，发布流程才进入停写、备份和正常模式切换。正常后端由 `deploy/docker-compose.prod.yml` 启动，`DEPLOYMENT_VALIDATION_MODE=false`，按正常策略执行已确认的 Flyway 迁移和启动任务；必须先通过后端健康检查和前端候选检查，才写入 `.deployed-state` 并启动正式前端；随后后端、容器内前端和公网根路径必须共同通过。任一健康检查失败都会保留错误输出、停止后续发布并进入既有回滚或提交后停止流程，不输出成功发布声明。

## 覆盖率门禁

前端基线来自 `npm run test:coverage` 对全部 37 个测试文件、153 个测试和 `src/**/*.{js,jsx}` 全部源文件的连续全量实测，不是测试数量或定向报告：

| 范围 | Statements | Branches | Functions | Lines |
| --- | ---: | ---: | ---: | ---: |
| 全部前端源文件 | 65.03% | 73.96% | 51.82% | 65.03% |
| `src/auth/**` | 87.40% | 93.33% | 62.50% | 87.40% |
| `src/pages/StrategyFormModal.jsx` | 93.87% | 86.11% | 83.33% | 93.87% |
| `src/pages/SettingsPage.jsx` | 98.93% | 30.00% | 60.00% | 98.93% |

`frontend/vite.config.js` 还锁定 API 客户端、定投预算与计划、交易编辑、导入、交易确认、定投/策略表单、设置页两条写路径、普通交易和基金编辑页面的逐文件四项覆盖率。新增设置页行为测试后连续两次全量运行都实测全局 65.03%/73.96%/51.82%/65.03%，设置页为 98.93%/30.00%/60.00%/98.93%；门禁使用这些重复实测值而不是零阈值。API 客户端分支仍采用既有连续实测 79.31%/78.57% 的保守下界 78.57%，避免把运行时插桩波动误判为代码回退。CI 用同一条全量命令执行这些阈值，任一低于下界即失败。后端 JaCoCo 已生成全量报告但尚未在 05/06 并发改动稳定后重新采集，因此未写猜测阈值；在后端全量 `verify` 实测并登记认证、账目和定投范围前，本 Issue 保持 pending。

## Actions 不可变版本

流水线所有 `uses:` 均固定到 2026-09-07 查询对应 major tag 后取得的完整提交 SHA，并在行尾保留 major 版本说明。接触 `GITHUB_TOKEN`、GHCR 写权限或 VPS 生产秘密的 checkout、login、build-push 和 SSH action 不使用浮动分支或标签；升级时必须重新核对上游提交和本仓权限/输入后显式替换 SHA。

## 监控与失败处理

1. 在 GitHub Actions 中先看 `Check tag points to main` 和 `Require same-commit CI`；任一失败时不得人工重跑部署 job 绕过门禁。
2. 候选阶段核对 VPS `candidate-validation/<release-tag>-attempt-1.log` 与 `attempt-2.log` 都以 `result=passed` 结束，且 `image=` digest 与工作流输出一致。
3. 正常切换阶段观察 deploy job、`docker compose -f deploy/docker-compose.prod.yml ps` 和 `docker compose -f deploy/docker-compose.prod.yml logs --tail=100 backend frontend`；只有工作流最终输出 `deployed <tag> (sha-<commit>)` 才能记录成功。
4. 正常健康失败时保留 Actions 日志、候选报告、`.deployed-state` 和容器日志；应用已由脚本停止或回滚，不手工补写成功状态。数据库可能迁移后失败时按脚本分类处理，不以旧镜像健康替代本次发布成功。

## 本地演练

仅连接隔离或生产等效数据库。PowerShell 示例：

```powershell
$env:SPRING_PROFILES_ACTIVE = 'validation'
$env:DB_URL = 'jdbc:postgresql://localhost:5432/fundpilot?options=-c%20default_transaction_read_only%3Don'
$env:DB_USERNAME = 'fundpilot'
$env:DB_PASSWORD = '<isolated-database-password>'
$env:YANGJIBAO_SECRET = 'local-validation-only'
.\backend\mvnw.cmd -f backend\pom.xml spring-boot:run
```

另开终端执行 `Invoke-RestMethod http://127.0.0.1:8080/actuator/health`，确认 `status` 为 `UP` 后停止进程并重复一次；同时核对两次日志中没有 Flyway migrate/repair、调度执行或启动写入。正常本地启动前清除 `SPRING_PROFILES_ACTIVE`，明确设置 `$env:DEPLOYMENT_VALIDATION_MODE = 'false'`，按相同端点重新健康检查。真实秘密不得写入命令历史或仓库文件。

不连接生产环境的流水线与覆盖率演练：

```powershell
node --test deploy/verify-release-gate.test.mjs
Set-Location frontend
npm ci
npm run lint
npm run test:coverage
npm run build
```

门禁 fixture 覆盖缺失、失败、异提交、缺少规定 job、规定 job 失败、API 超时信号、固定 `ci.yml` 来源及同一 attempt job 请求。需要验证覆盖率失败路径时，可运行 `npm exec vitest -- run src/dcaBudget.test.js --coverage --coverage.thresholds.lines=100`；该定向命令预期退出 1，仅是故障注入，不是基线采集。后端工作树稳定后由主代理串行运行 `./backend/mvnw -B -f backend/pom.xml clean verify`，从新生成的 `backend/target/site/jacoco/jacoco.xml` 登记全量及认证、账目、定投基线，再配置 JaCoCo check。
