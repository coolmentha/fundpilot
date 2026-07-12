# Operations Security And Release Safety

## 1. Scope / Trigger

适用于 `/api/admin/**` 管理入口、Flyway 校验/repair、GitHub Actions CI、生产 Compose 发布、数据库备份和失败回滚。任何改动触及管理凭据、迁移历史、发布 tag、健康检查或数据库恢复时，必须按本契约检查。

## 2. Signatures

```text
HTTP Header: X-Admin-Key: <secret>
Config: fundpilot.admin.api-key=${ADMIN_API_KEY:}
Config: fundpilot.flyway.legacy-v7-repair.enabled=${FLYWAY_REPAIR_LEGACY_V7:false}
Config: fundpilot.deployment.validation-mode=${DEPLOYMENT_VALIDATION_MODE:false}

POST /api/admin/fund-dict/sync
POST /api/admin/market-data/refresh
POST /api/admin/market-data/sync-trading-calendar
POST /api/admin/signals/generate
POST /api/admin/transactions/confirm-nav
```

```java
void LegacyV7FlywayRepairService.migrate(Flyway flyway);
FlywayMigrationStrategy FlywayMigrationConfig.flywayMigrationStrategy();
```

生产 `.env` 必须提供 `DB_USERNAME`、`DB_PASSWORD`、`ADMIN_API_KEY`、`BACKEND_IMAGE`、`FRONTEND_IMAGE`；镜像必须使用 `image@sha256`。仅已知遗留 V7 修复场景设置 `FLYWAY_REPAIR_LEGACY_V7=true`。

## 3. Contracts

- `AdminApiKeyFilter` 只保护 `/api/admin` 和 `/api/admin/**`；公共 API、Actuator 与定时任务不受影响。
- 管理 Key 使用常量时间比较，不进入 URL、请求体、日志、localStorage 或前端构建变量。
- 前端只在管理操作请求中发送 `X-Admin-Key`，Key 仅保存在 `AdminPage` 组件内存，刷新页面即清空。
- 服务端 Key 未配置时失败关闭，不得匿名放行。
- Flyway 默认严格校验，禁止 `ignoreMigrationPatterns: '*:missing'` 或 `versioned:missing`。
- repair 开关开启时，只有唯一 `MISSING_SUCCESS`、version `7`、description `dca take profit replaces timing`、script `V7__dca_take_profit_replaces_timing.sql` 可以进入 repair。
- repair 前拒绝其他 missing/future/failed 与 checksum、description、type 不匹配；repair 后必须只有 V7 为 `DELETED`，不得 removed 或 aligned 其他迁移。
- 前端 CI 固定执行 `npm ci -> npm run lint -> npm test -> npm run build`；tag 发布工作流必须重新执行后端 verify 和前端完整验证。
- Git `v*` tag 只用于检出 Compose；实际部署和回滚必须使用构建步骤返回的 backend/frontend GHCR digest。
- 发布工作流使用 concurrency 串行化，且必须确认 release tag 仍指向本次构建 commit。
- 候选镜像必须在停写前拉取。进入维护前检查未来 60 分钟安全区间不得与北京时间 `02:00-04:15`、工作日 `14:00-15:15` 相交；正常发布使用 30 分钟绝对截止，总维护/回滚使用 55 分钟截止并预留 5 分钟紧急停服。
- 维护和回滚中的 `docker`、Compose、`pg_dump`、`pg_restore`、探活等外部命令必须用绝对截止时间与 `timeout --foreground --kill-after=5s` 双重约束。提交前命令按 30 分钟前向截止裁剪，rollback/提交后命令按 55 分钟总截止裁剪，禁止只依赖 Bash 收到 TERM 后执行 trap。
- 部署在同一 SSH 脚本中用上一 release 的 Compose 停止 frontend/backend、等待固定 `fundpilot-db` 容器、生成并校验 `pg_dump -Fc`。
- 候选 backend 使用 `DEPLOYMENT_VALIDATION_MODE=true`：不注册 Scheduler，Pending 补偿和交易日历启动监听器不得写库。
- 候选 frontend 仅接入内部 `fundpilot_default` 网络，必须验证静态页和 `/api/funds` 反代，不得提前连接外部 Caddy 网络。
- 容器内 Nginx 探活必须使用 `http://127.0.0.1/`，禁止使用可能优先解析到未监听 IPv6 回环的 `localhost`。
- 远程 Compose 命令必须显式传 `--env-file "$VPS_PATH/.env"`；切换到上一 release 后仍从根目录环境文件读取数据库凭据，禁止依赖 Compose 随版本/工作目录变化的隐式 `.env` 搜索。
- 候选验证通过后原子提交 `.deployed-state` 并解除数据库回滚，再以正常模式重启 backend、接入正式 frontend，并验证公网首页和 `/api/funds`。
- 发布失败时先保持应用停止，恢复发布前数据库备份，再启动上一 tag；恢复失败或没有上一 tag 时保持应用停止并让工作流失败。
- 提交后的正常 backend 或公网 frontend 启动失败时停止对外服务并报错，不得恢复旧数据库覆盖后台任务或用户写入。
- ERR/HUP/INT/TERM 始终进入同一个阶段分派 trap；以原子落盘 `.deployed-state` 中本次 `DEPLOYMENT_TOKEN` 判断是否已提交。提交前恢复数据库和旧状态，提交后只停止 backend/frontend，不得依赖相邻两行命令切换 trap。
- `.deployed-state` 提交后必须立即取消 30 分钟前向 watchdog；提交后命令只受 55 分钟总截止约束。
- rollback 或 post-commit 探活失败后的最终停服使用独立 `timeout --kill-after`，不得因总 deadline 已耗尽而跳过停服。
- `.env`、状态临时文件和数据库备份从创建起必须受 `umask 077` 保护；数据库恢复使用单事务。

## 4. Validation & Error Matrix

| 条件 | 行为 | HTTP / 结果 |
|---|---|---|
| `ADMIN_API_KEY` 为空 | 管理请求失败关闭 | 503 `ADMIN_AUTH_NOT_CONFIGURED` |
| 管理 Header 缺失或不匹配 | 不进入 Controller | 401 `ADMIN_UNAUTHORIZED` |
| 管理 Header 匹配 | 执行对应管理 Service | 原端点响应 |
| Flyway repair 开关关闭 | 直接严格 migrate/validate | 任意 missing 导致启动失败 |
| 开关开启且无遗留 V7 | 不调用 repair，严格 validate/migrate | 正常启动 |
| 唯一已知 V7 Missing | 仅将 V7 标为 `DELETED` | validate/migrate 后启动 |
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

- Good：浏览器内存中输入管理 Key，只有 `/api/admin/signals/generate` 请求带 Header，普通基金查询不带凭据。
- Good：生产存在唯一旧 V7 Missing，repair 标记 `DELETED` 后 V1-V18 严格校验通过。
- Good：候选 backend 无调度/启动写，候选 frontend 只在内部网络完成 API 反代验证，失败时安全恢复备份。
- Good：部署使用 backend/frontend digest；Git tag 被移动时在停写前拒绝发布。
- Good：镜像先拉取，维护前确认未来 60 分钟不跨 cron 禁区；前向命令按 30 分钟截止，rollback/提交后按 55 分钟截止，命令忽略 TERM 时 5 秒后强制 KILL。
- Good：状态文件原子提交后收到 HUP，统一 trap 读取本次 `DEPLOYMENT_TOKEN` 并只停止应用，不恢复数据库。
- Good：新版本候选验证失败，部署脚本恢复停写前备份，使用 `.deployed-state` 的旧 release/digest 重新健康启动。
- Base：repair 开关保持开启但旧 V7 已修复，不调用 repair，继续严格 validate/migrate。
- Base：首次部署没有上一 tag，失败时恢复数据库并保持应用停止。
- Bad：把管理 Key 编进 `VITE_*`，密钥会出现在公开 JS 中。
- Bad：只隐藏前端管理菜单，后端端点仍可匿名调用。
- Bad：使用 `*:missing` 让任意缺失迁移继续启动。
- Bad：新版本运行且允许写入后再恢复部署前备份，会覆盖用户新数据。
- Bad：使用 `latest` 回滚，无法证明恢复的是哪一版。
- Bad：候选 backend 开着 Scheduler 等待前端探活，回滚会删除这段窗口的定时写入。
- Bad：正式 frontend 接入 Caddy 后仍允许数据库回滚，会覆盖健康等待窗口的用户写入。
- Bad：只在脚本启动时检查当前时刻，镜像拉取或备份变慢后仍可能跨入 cron 窗口。
- Bad：先 `mv .deployed-state`、下一行才切换 trap，两个命令之间的信号会走错回滚阶段。

## 6. Tests Required

- `AdminApiKeyFilterTest`：正确、缺失、错误、未配置 Key 及公共路径。
- `AdminApiKeyIntegrationTest`：真实 Spring Web 过滤链验证 401/200 与公共 API 放行。
- `frontend/src/api/client.test.js` / `hooks.test.js`：调用方 Header 合并、五个管理 action 路由和未知 action 拒绝。
- `LegacyV7FlywayRepairServiceTest`：开关关闭、幂等、额外 missing、failed、元数据漂移及越权 repair 结果。
- `LegacyV7FlywayRepairIntegrationTest`：真实 PostgreSQL 独立 schema 中插入旧 V7 history，repair 后状态为 `DELETED` 且严格 validate 成功。
- CI YAML 必须可解析，部署脚本必须通过 `bash -n`，Compose 必须通过 `docker compose config --quiet`。
- 部署脚本复核必须覆盖禁区交叉计算、绝对 deadline、命令级 timeout，以及 `.deployed-state` 原子提交前后的信号分派。
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

```java
verifyNoFailedOrMismatchedMigrations(flyway.info().all());
repairOnlyExpectedLegacyV7();
verifyV7IsDeleted();
flyway.validate();
flyway.migrate();
```

```sh
stop_previous_release
backup_and_verify_database
start_read_only_candidate_backend
smoke_candidate_frontend_on_internal_network
commit_digest_state
start_normal_backend_and_public_frontend
```
