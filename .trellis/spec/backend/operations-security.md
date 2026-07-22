# Operations Security And Release Safety

## 1. Scope / Trigger

适用于全站 `/api/**` 访问鉴权、Flyway 校验/repair、GitHub Actions CI、生产 Compose 发布、数据库备份和失败回滚。任何改动触及访问凭据、迁移历史、发布 tag、健康检查或数据库恢复时，必须按本契约检查。

## 2. Signatures

```text
HTTP Header: X-Admin-Key: <secret>
Browser session: HttpOnly host-only cookie fundpilot_session=<signed opaque token>
Config: fundpilot.admin.api-key=${ADMIN_API_KEY:}
Config: fundpilot.flyway.legacy-v7-repair.enabled=${FLYWAY_REPAIR_LEGACY_V7:false}
Config: fundpilot.deployment.validation-mode=${DEPLOYMENT_VALIDATION_MODE:false}

POST /api/admin/fund-dict/sync
POST /api/admin/market-data/refresh
POST /api/admin/market-data/sync-trading-calendar
POST /api/admin/signals/generate
POST /api/admin/transactions/confirm-nav
GET /api/auth/verify
```

```java
void LegacyV7FlywayRepairService.migrate(Flyway flyway);
FlywayMigrationStrategy FlywayMigrationConfig.flywayMigrationStrategy();
```

生产 `.env` 必须提供 `DB_USERNAME`、`DB_PASSWORD`、`ADMIN_API_KEY`、`BACKEND_IMAGE`、`FRONTEND_IMAGE`；镜像必须使用 `image@sha256`。仅已知遗留 V7 修复场景设置 `FLYWAY_REPAIR_LEGACY_V7=true`。

## 3. Contracts

- `AdminApiKeyFilter` 保护 `/api` 和所有 `/api/**`；静态资源、Actuator 与定时任务不受影响。
- 访问 Key 使用常量时间比较，不进入 URL、请求体、日志、localStorage、sessionStorage、Cookie 或前端构建变量。浏览器只在登录请求 Header 中提交一次 Key，后端签发不含原始 Key 的 HMAC 签名会话 Cookie。
- 会话 Cookie 必须为 host-only、`HttpOnly`、`SameSite=Strict`，生产 HTTPS 下带 `Secure`，有效期 30 天。业务 API 同时接受有效会话 Cookie和 `X-Admin-Key`，后者仅供脚本、部署探活和首次登录。
- 未认证时前端只渲染登录页，业务路由与查询 hooks 不得挂载。页面启动通过 `GET /api/auth/verify` 重验 Cookie，验证成功前不得挂载业务界面。
- 启动重验仅在 401 时进入登录页；网络错误、超时和 5xx 保留 Cookie并显示重试态。所有前端 API 请求必须有统一超时。
- 主动退出调用后端清除 Cookie，并用不含凭据的 localStorage logout 事件同步其他标签页。
- 已认证请求返回 401 时必须推进认证代次、清空 React Query 缓存并回到登录页；管理页复用全站登录态，不得再次采集 Key。
- API client 必须按认证代次关联请求；旧代次的迟到 401 不得退出已完成的新登录。
- 所有 `/api/**` 成功和失败响应必须返回 `Cache-Control: no-store, private` 与 `Vary: X-Admin-Key`，退出后不得依赖浏览器或代理继续持有敏感响应。
- 服务端 Key 未配置时失败关闭，不得匿名放行。
- Flyway 默认严格校验，禁止 `ignoreMigrationPatterns: '*:missing'` 或 `versioned:missing`。
- repair 开关开启时，只有唯一 `MISSING_SUCCESS`、version `7`、description `dca take profit replaces timing`、script `V7__dca_take_profit_replaces_timing.sql` 可以进入 repair。
- repair 前拒绝其他 missing/future/failed 与 checksum、description、type 不匹配；repair 后必须只有 V7 为 `DELETED`，不得 removed 或 aligned 其他迁移。合法 pending migration 必须先正常 migrate，再执行最终严格 validate，禁止用前置 validate 阻断版本升级。
- 前端 CI 固定执行 `npm ci -> npm run lint -> npm test -> npm run build`；发布只允许在已通过 CI 的 `main` 提交上创建 tag，tag 发布工作流不重复执行后端 verify 或前端完整验证。
- backend/frontend 镜像构建使用独立 scope 的 GitHub Actions Buildx 缓存；VPS 并行拉取两个候选镜像但必须等待且确认两者都成功。仅无状态 frontend 使用 2 秒停止宽限期，backend 保留默认安全停止时间。
- Git `v*` tag 只用于检出 Compose；实际部署和回滚必须使用构建步骤返回的 backend/frontend GHCR digest。
- 发布工作流使用 concurrency 串行化，且必须确认 release tag 仍指向本次构建 commit。
- 监控栈独立手动维护；应用 `deploy.yml` 不得 checkout、改权限或重启 Promtail/Grafana。
- 候选镜像必须在停写前拉取。进入维护前检查未来 60 分钟安全区间不得与北京时间 `02:00-04:15`、工作日 `14:00-15:15` 相交；正常发布使用 30 分钟绝对截止，总维护/回滚使用 55 分钟截止并预留 5 分钟紧急停服。
- 维护和回滚中的 `docker`、Compose、`pg_dump`、`pg_restore`、探活等外部命令必须用绝对截止时间与 `timeout --foreground --kill-after=5s` 双重约束。提交前命令按 30 分钟前向截止裁剪，rollback/提交后命令按 55 分钟总截止裁剪，禁止只依赖 Bash 收到 TERM 后执行 trap。
- 部署在同一 SSH 脚本中用上一 release 的 Compose 停止 frontend/backend、等待固定 `fundpilot-db` 容器、生成并校验 `pg_dump -Fc`。
- 候选 backend 使用 `DEPLOYMENT_VALIDATION_MODE=true`：不注册 Scheduler，Pending 补偿和交易日历启动监听器不得写库。
- 候选 frontend 仅接入内部 `fundpilot_default` 网络，必须验证静态页和带 `X-Admin-Key` 的 `/api/funds` 反代，不得提前连接外部 Caddy 网络。
- 部署探活的 Key 必须通过 stdin 传给 HTTP 客户端，不得出现在 `curl`、`wget`、`docker` 或 `timeout` 的进程参数中；回滚探活必须从旧 `.env` 读取上一版本 Key，并兼容不要求 Key 的旧 release。
- 候选前端的原始 HTTP 探活必须在容器内读取 stdin 中的 Key，由容器内管道构造请求并短暂保持写端打开，同时在宿主机完整捕获响应后再匹配 JSON；禁止让 BusyBox `nc` 直接收到请求端 EOF，或把其输出直接接到 `grep -q`，两者都会提前关闭连接并让有反代延迟的 Nginx 请求记录 `499`。
- 容器内 Nginx 探活必须使用 `http://127.0.0.1/`，禁止使用可能优先解析到未监听 IPv6 回环的 `localhost`。
- 远程 Compose 命令必须显式传 `--env-file "$VPS_PATH/.env"`；切换到上一 release 后仍从根目录环境文件读取数据库凭据，禁止依赖 Compose 随版本/工作目录变化的隐式 `.env` 搜索。
- 候选验证通过后原子提交 `.deployed-state` 并解除数据库回滚，再以正常模式重启 backend、接入正式 frontend，并验证公网首页和带 `X-Admin-Key` 的 `/api/funds`。
- 发布失败时先保持应用停止，恢复发布前数据库备份，再启动上一 tag；恢复失败或没有上一 tag 时保持应用停止并让工作流失败。
- 提交后的正常 backend 或公网 frontend 启动失败时停止对外服务并报错，不得恢复旧数据库覆盖后台任务或用户写入。
- ERR/HUP/INT/TERM 始终进入同一个阶段分派 trap；以原子落盘 `.deployed-state` 中本次 `DEPLOYMENT_TOKEN` 判断是否已提交。提交前恢复数据库和旧状态，提交后只停止 backend/frontend，不得依赖相邻两行命令切换 trap。
- `.deployed-state` 提交后必须立即取消 30 分钟前向 watchdog；提交后命令只受 55 分钟总截止约束。
- 前向 watchdog 停止时必须终止并等待其 sleep 子进程，禁止后台后代继续持有 SSH 输出管道。仓库 clone/fetch 和候选镜像 pull 必须分别有硬超时；SSH Action 的 `command_timeout` 必须覆盖这些前置上限、55 分钟总维护截止和至少 5 分钟清理余量。
- rollback 或 post-commit 探活失败后的最终停服使用独立 `timeout --kill-after`，不得因总 deadline 已耗尽而跳过停服。
- `.env`、状态临时文件和数据库备份从创建起必须受 `umask 077` 保护；数据库恢复使用单事务。

## 4. Validation & Error Matrix

| 条件 | 行为 | HTTP / 结果 |
|---|---|---|
| `ADMIN_API_KEY` 为空 | 所有 `/api/**` 请求失败关闭 | 503 `ADMIN_AUTH_NOT_CONFIGURED` |
| API Header 缺失或不匹配 | 不进入 Controller | 401 `ADMIN_UNAUTHORIZED` |
| API Header 匹配 | 执行对应业务 Controller | 原端点响应 |
| 会话 Cookie 缺失/无效 | 显示登录页，不挂载业务路由 | 等待用户输入 |
| 会话 Cookie 重验成功 | 挂载业务路由 | 恢复登录态 |
| 重验网络错误/超时/5xx | 保留 Cookie和查询隔离 | 显示重试/重新登录 |
| 用户主动退出或已认证请求 401 | 清 Cookie、内存认证代次和查询缓存 | 所有标签页返回登录页 |
| 静态资源或 Actuator | 不经过 Key Filter | 原端点响应 |
| Flyway repair 开关关闭 | 直接严格 migrate/validate | 任意 missing 导致启动失败 |
| 开关开启且无遗留 V7 | 不调用 repair，正常 migrate 后严格 validate | 正常启动 |
| 唯一已知 V7 Missing | 仅将 V7 标为 `DELETED` | repair/migrate/validate 后启动 |
| 其他 missing/failed/元数据漂移 | 禁止 repair | 启动失败 |
| 数据库备份为空或不可列出 | 不启动新版本 | 部署失败 |
| 新版本健康检查失败 | 恢复数据库和上一 tag | 回滚后工作流仍失败 |
| 候选 backend/frontend 验证失败 | 尚未接入公网，恢复数据库和上一 digest | 回滚后工作流仍失败 |
| 提交后正常 backend 或公网入口失败 | 停止对外服务，不恢复数据库 | 工作流失败，保留新数据库状态 |
| 未来 60 分钟安全区间与定时任务禁区相交 | 不进入维护窗口 | 发布失败，旧版本继续运行 |
| 正常发布超过 30 分钟 | watchdog 发送 TERM，按当前提交阶段处理 | 发布失败 |
| 外部命令超过阶段/命令上限 | 先 TERM，5 秒后 KILL；恢复失败则保持应用停止 | 发布失败 |
| 数据库恢复失败 | 不启动任何应用版本 | 保持维护状态并失败 |

## 5. Good / Base / Bad Cases

- Good：登录请求验证 Key 后签发 HttpOnly 持久会话 Cookie；刷新、关闭或重启浏览器后先重新验证 Cookie，成功才恢复业务界面。
- Good：生产存在唯一旧 V7 Missing，repair 标记 `DELETED` 后 V1-V18 严格校验通过。
- Good：候选 backend 无调度/启动写，候选 frontend 只在内部网络完成 API 反代验证，失败时安全恢复备份。
- Good：候选前端原始 HTTP 探活在容器内保持请求写端打开，宿主完整读取响应后再判断成功，Nginx 反代慢于静态页时仍返回 200。
- Good：部署使用 backend/frontend digest；Git tag 被移动时在停写前拒绝发布。
- Good：镜像先拉取，维护前确认未来 60 分钟不跨 cron 禁区；前向命令按 30 分钟截止，rollback/提交后按 55 分钟截止，命令忽略 TERM 时 5 秒后强制 KILL。
- Good：状态文件原子提交后收到 HUP，统一 trap 读取本次 `DEPLOYMENT_TOKEN` 并只停止应用，不恢复数据库。
- Good：新版本候选验证失败，部署脚本恢复停写前备份，使用 `.deployed-state` 的旧 release/digest 重新健康启动。
- Base：repair 开关保持开启但旧 V7 已修复，不调用 repair，继续严格 validate/migrate。
- Base：首次部署没有上一 tag，失败时恢复数据库并保持应用停止。
- Bad：把访问 Key 编进 `VITE_*`，密钥会出现在公开 JS 中。
- Bad：只做前端登录页，后端普通业务 API 仍可匿名调用。
- Bad：把原始 Key 写入 localStorage/sessionStorage/普通 Cookie，或读取浏览器状态后不经服务端验证就挂载业务界面。
- Bad：使用 `*:missing` 让任意缺失迁移继续启动。
- Bad：新版本运行且允许写入后再恢复部署前备份，会覆盖用户新数据。
- Bad：使用 `latest` 回滚，无法证明恢复的是哪一版。
- Bad：候选 backend 开着 Scheduler 等待前端探活，回滚会删除这段窗口的定时写入。
- Bad：正式 frontend 接入 Caddy 后仍允许数据库回滚，会覆盖健康等待窗口的用户写入。
- Bad：只用静态 Nginx 或直接后端验证 BusyBox `nc`；这无法复现 Nginx 等待上游时因客户端 EOF 产生的 `499`。
- Bad：只在脚本启动时检查当前时刻，镜像拉取或备份变慢后仍可能跨入 cron 窗口。
- Bad：先 `mv .deployed-state`、下一行才切换 trap，两个命令之间的信号会走错回滚阶段。

## 6. Tests Required

- `AdminApiKeyFilterTest`：普通 API 的正确、缺失、错误、未配置 Key，编码/矩阵绕过路径及 Actuator 放行。
- `AdminApiKeyIntegrationTest`：真实 Spring Web 过滤链验证普通 API 与 `/api/auth/verify` 的 401/200，以及 Actuator 放行。
- `frontend/src/auth/SiteAuthGate.test.jsx` / `api/client.test.js` / `siteAuthStorage.test.js` / `hooks.test.js`：一次性 Header 登录、Cookie 启动重验、暂时故障重试、401 退出、认证代次隔离、跨标签页退出、五个管理 action 路由和未知 action 拒绝。
- `LegacyV7FlywayRepairServiceTest`：开关关闭、幂等、额外 missing、failed、元数据漂移及越权 repair 结果。
- `LegacyV7FlywayRepairIntegrationTest`：真实 PostgreSQL 独立 schema 中插入旧 V7 history，并保留至少一个合法 pending migration；repair 后 V7 为 `DELETED`、pending 成功应用且严格 validate 成功。
- CI YAML 必须可解析，部署脚本必须通过 `bash -n`，Compose 必须通过 `docker compose config --quiet`。
- 部署脚本复核必须覆盖禁区交叉计算、绝对 deadline、命令级 timeout，以及 `.deployed-state` 原子提交前后的信号分派。
- 候选前端探活回归必须运行项目真实 frontend Nginx，并用至少延迟 2 秒的测试 backend 验证：直接 EOF 的旧管道产生 `499`，保持写端并完整捕获响应的新管道返回 200 和 `"success":true`。
- `SchedulingConfigTest`、`PendingTransactionCompensationJobTest`、`TradingCalendarSyncJobTest`：候选模式不注册调度且跳过启动写。
- 修改后运行后端全量测试，以及前端 lint、test、build。

## 7. Wrong vs Correct

### Wrong

```yaml
spring:
  flyway:
    ignoreMigrationPatterns: '*:missing'
```

```javascript
const adminKey = import.meta.env.VITE_ADMIN_KEY;
localStorage.setItem('admin-key', adminKey);
```

```sh
docker compose down
TAG=latest docker compose up -d
# 健康检查失败后没有数据库恢复
```

### Correct

```java
if (configuredKey.isBlank()) reject(503, ADMIN_AUTH_NOT_CONFIGURED);
if (!MessageDigest.isEqual(expected, supplied)) reject(401, ADMIN_UNAUTHORIZED);
```

```javascript
await loginSiteApiKey(candidate); // 主 Key 只提交一次，服务端签发 HttpOnly 会话 Cookie。
await verifySiteSession();        // 刷新/重开只验证会话，不读取或重传主 Key。
// 只有明确 401 才回到登录页；网络、超时和 5xx 保留会话并提供重试。
await logoutSiteSession();
broadcastSiteLogout();            // 同源其他标签页立即退出。
```

```java
verifyNoFailedOrMismatchedMigrations(flyway.info().all());
repairOnlyExpectedLegacyV7();
verifyV7IsDeleted();
flyway.migrate();
flyway.validate();
```

```sh
stop_previous_release
backup_and_verify_database
start_read_only_candidate_backend
smoke_candidate_frontend_on_internal_network
commit_digest_state
start_normal_backend_and_public_frontend
```
