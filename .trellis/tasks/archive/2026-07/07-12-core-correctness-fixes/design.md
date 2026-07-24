# 技术设计

## 1. 止盈双净值边界

`MarketIndicatorSnapshot.currentNav` 保持累计净值，不改变已有逻辑止损分析语义。`SignalGenerationService` 从同一条最近
`fund_nav_history` 读取单位净值与累计净值，并将二者同时传给止盈生命周期和纯策略上下文，禁止把 14:50 snapshot
与更晚入库的净值历史跨日混算。

- 单位净值：持仓市值、浮盈、整体收益、建议卖出份额。
- 累计净值：周期峰值、创新高、峰值回撤。
- 缺少任一值：本轮止盈评估禁用。

不修改 `fund_strategy.cycle_peak_nav`，历史值继续有效。

## 2. 定投事务边界

新增 `DcaSuggestionService`，承接单基金生成逻辑并提供 `@Transactional generateForFund`。`DcaSuggestionJob`
只负责交易日门控、枚举基金、逐只调用和失败隔离。

## 3. ADJUST 强不变量

`FundTransactionService.createManual` 在 ADJUST 分支先悲观锁定基金行，再校验事实持仓。合法交易保存后依次更新 lot、调用
`FundPositionService.reconcileStatus`，全部位于同一事务。

`ADJUST_OUT > confirmedHoldingShares` 抛新的业务错误码。lot 少于事实持仓仍视为合法未跟踪调整份额，不改变现有零费率降级语义。

## 4. 交易日双层门控

- Job 层：避免非交易日抓取外部行情和触发信号。
- Service 层：保护管理员直接生成信号的入口。

日期统一通过 `ChinaTradingDate.toUtcDate(clock.instant())`，交易日事实来自 `TradingCalendarService`。

## 5. 信号可操作性

新增 `SignalActionabilityService` 作为单一规则入口，供 pending 查询、确认、忽略和 DTO 状态映射复用。

状态仅在 API 投影中存在：

- `INFORMATIONAL`：NONE。
- `RESPONDED`：存在未软删关联交易。
- `IGNORED`：`ignoredDate != null`。
- `PENDING`：满足有效期规则。
- `EXPIRED`：其余未处理非 NONE 信号。

普通 SignalLog 只在最近交易日有效。当前策略 `takeProfitPhase=TRIGGERED` 且 `triggeredSignalId` 指向该信号时跨日有效。

若获 schema 授权，V18 只增加 `signal_log.ignored_date`，不存重复的 status 或 expiresDate。忽略当前止盈信号时复用取消交易的生命周期恢复规则。

## 6. 测试数据库隔离

本地 Docker 当前不可用，因此不把 Testcontainers 作为唯一默认路径。优先使用同一 PostgreSQL 实例中的独立 `fundpilot_test`
schema，并在集成测试启动前清理/迁移该 schema；开发 `public` schema 永不作为 test profile 默认目标。CI workflow 暂不改动。

## 7. 前端与文档

确认页读取已有 `FundView.holdingShares`，提供份额上限和快捷回填。查询失败复用 `QueryErrorState`。`PRODUCT.md` 做当前模型重写，
`CONTEXT.md` 明确 BUILD/ADD 仅为存量兼容。

## 8. 全站 API Key 与持久会话

`AdminApiKeyFilter` 拦截 `/api` 与所有 `/api/**`。服务端从 `fundpilot.admin.api-key` 读取 Key，使用常量时间比较：

- 配置为空：503 `ADMIN_AUTH_NOT_CONFIGURED`，失败关闭。
- 请求缺失或不匹配：401 `ADMIN_UNAUTHORIZED`。
- Header 匹配或存在有效签名会话 Cookie：继续 Controller 调用。

浏览器登录时只在 `POST /api/auth/login` Header 中提交一次 Key。后端签发 30 天、host-only、HttpOnly、SameSite=Strict 的 HMAC
会话 Cookie，HTTPS 下增加 Secure。启动通过 `/api/auth/verify` 恢复会话；主动退出清 Cookie并广播不含凭据的 localStorage
事件。管理页复用全站会话，不再次采集或保存 Key。

## 9. Flyway 遗留修复

移除 `ignoreMigrationPatterns: '*:missing'`，恢复默认严格校验。新增显式开关 `fundpilot.flyway.repair-legacy-v7`：

1. 默认关闭，直接执行严格 migrate。
2. 开启时读取 `flyway.info()`，仅允许唯一 `MISSING_SUCCESS` 为版本 7、脚本 `V7__dca_take_profit_replaces_timing.sql`。
3. 发现其他 Missing、失败状态或已应用迁移元数据不匹配时立即终止，不调用 repair。
4. 调用 repair 后确认 `migrationsDeleted` 仅含 V7，且没有 removed/aligned 项，再执行 migrate。

repair 只修复 Flyway 元数据，不执行旧 V7 SQL。部署失败时数据库从发布前备份恢复。

## 10. CI 与部署事务

前端 CI 使用 `npm ci -> lint -> test -> build`。

部署改为单 SSH step：

1. 校验 `RELEASE_TAG`、凭据和部署目录。
2. 读取 `.deployed-state` 或旧 `.env` 中的上一 release 与镜像 digest。
3. 使用上一 release 的 Compose 停止 frontend/backend，通过固定 `fundpilot-db` 创建并校验 `pg_dump -Fc` 备份。
4. 候选 backend 禁用 Scheduler 与启动写监听器；候选 frontend 只在内部网络验证静态页和 API 反代。
5. 候选通过后原子更新 `.deployed-state`，解除数据库回滚，再以正常模式重启 backend 并接入公网 frontend。
6. 提交前失败恢复数据库和上一 digest；提交后失败停止对外服务但不恢复数据库，避免覆盖后台或用户写入。

候选镜像在停写前拉取；进入发布维护前检查未来 60 分钟安全区间不得与北京时间 `02:00-04:15` 及工作日 `14:00-15:15`
的不可补偿定时任务相交。提交前命令按 30 分钟前向截止裁剪，状态提交后立即取消前向 watchdog；rollback/提交后命令按 55
分钟总截止裁剪，所有外部命令通过 `timeout --foreground --kill-after=5s` 硬限制，另预留 5 分钟独立紧急停服。ERR/HUP/INT/TERM
使用单一 trap，按原子状态文件中的本次部署令牌判断提交阶段：提交前回滚，提交后只做失败关闭且不恢复数据库。

容器内 Nginx 探活固定使用 IPv4 回环 `127.0.0.1`。所有远程 Compose 调用显式传根目录 `.env`，保证切换上一 release
后仍能读取数据库凭据并安全恢复旧版本。

维护窗口阻断部署期间写入，避免数据库恢复覆盖新写入。

## Compatibility

- 历史 BUILD/ADD/REBALANCE 等枚举保留。
- 历史普通信号按交易日动态过期。
- V20 为 `user_config` 恢复 `total_capital`，为 `fund` 增加 `max_position_ratio`；不从交易历史猜测总资金池。
- 存量持仓允许继续展示和卖出；新的买入确认必须先通过总池与单基金上限校验。
- 已有关联 CANCELLED 交易的信号仍视为已回应，不重新进入 pending。
- V18 不回填历史行，`ignored_date` 默认 null。

## Rollback

- R1-R4 均为代码级变更，可逐提交回滚。
- V18 加列为向后兼容变更；回滚应用时旧版本忽略新列。
- 测试 schema 与开发 schema 分离，清理仅作用于测试 schema。
- 主动退出可立即清除浏览器会话；服务端 Key 轮换会让旧签名会话失效，且不提供匿名降级。
- 部署失败恢复发布前数据库与上一镜像 tag；首次部署无上一 tag 时恢复数据库后保持应用停止。
