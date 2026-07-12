# 实施计划

## 阶段 1：核心正确性（无需 schema 变更）

- [x] 为止盈双净值口径补失败回归测试。
- [x] 拆分单位净值与累计净值输入，保持周期峰值兼容。
- [x] 将单基金定投生成迁移到独立事务 Service。
- [x] 补真实非测试事务调用和逐基金失败隔离测试。
- [x] 为 ADJUST 加基金行锁、超持仓校验和状态重算。
- [x] 补 ADJUST_IN/HOLDING、ADJUST_OUT/CLEARED、并发保护测试。
- [x] 为行情 Job 和信号 Service 增加交易日门控。
- [x] 运行阶段 1 聚焦测试。

## 阶段 2：信号生命周期（等待 schema 授权）

- [x] 新增 V18 `signal_log.ignored_date`。
- [x] 新增 SignalActionStatus 与 SignalActionabilityService。
- [x] pending 查询过滤过期/忽略信号，并保留 TRIGGERED 止盈例外。
- [x] 新增忽略 API，确认路径拒绝忽略/过期信号。
- [x] 忽略当前止盈信号时恢复 ARMED。
- [x] 补迁移、查询、确认/忽略并发与生命周期测试。

## 阶段 3：前端与文档

- [x] 确认页改为 SELL 主流程，增加忽略、当前持仓、份额上限和快捷操作。
- [x] 信号页展示生命周期状态，查询失败使用持久错误态。
- [x] 修正设置页副标题。
- [x] 重写 `docs/PRODUCT.md` 当前模型。
- [x] 清理 `CONTEXT.md` 内部矛盾和旧金字塔语义。
- [x] 将行情工作台任务文档的资金流向合同对齐当前实现。

## 阶段 4：测试隔离

- [x] test profile 使用独立 schema，禁止默认连接开发 public schema。
- [x] 集成测试启动时只清理/迁移测试 schema。
- [x] 验证重复运行全量测试结果稳定。
- [x] 不修改 CI workflow，除非另获授权。

## 阶段 5：审查剩余风险修复

- [x] 新增管理 API Key 过滤器、错误码和后端测试。
- [x] 管理页增加内存态凭据输入，管理请求单独携带 Header，并补前端测试。
- [x] 移除 Flyway 全局 missing 忽略，新增已知 V7 受控 repair 与测试。
- [x] CI 增加前端 lint/test 门禁，tag 发布前重复执行完整 verify。
- [x] 部署改为停写备份、digest 制品、候选隔离验证、提交前失败恢复数据库和上一 release。
- [x] 更新部署示例环境变量和事务一致性/运维规范。
- [x] 运行后端、前端、工作流语法与 Trellis 全量验证。

## 验证命令

```powershell
cd backend
.\mvnw.cmd -Dtest=TakeProfitLifecycleServiceTest,SignalGenerationServiceTest test
.\mvnw.cmd -Dtest=DcaSuggestionServiceTest,DcaSuggestionJobTest test
.\mvnw.cmd -Dtest=FundTransactionServiceTest,FundPositionServiceTest,TransactionConfirmSupportTest test
.\mvnw.cmd -Dtest=MarketDataFetchJobTest,SignalQueryServiceTest,SignalOperationServiceTest test
.\mvnw.cmd test

cd ..\frontend
npm run lint
npm test
npm run build

cd ..
git diff --check
python .\.trellis\scripts\task.py validate 07-12-core-correctness-fixes
```

## 风险与回滚点

- 止盈峰值口径不可被误改为单位净值。
- DCA 拆 Service 后必须保留逐基金失败隔离。
- ADJUST 超持仓校验必须允许合法未跟踪份额。
- 信号过期规则必须保留 TRIGGERED 止盈跨日例外。
- 测试清理只能作用于独立测试 schema。
- 管理 Key 不得落入日志、URL、请求体、持久浏览器存储或前端构建变量。
- Flyway repair 只能处理唯一已知 V7，任何额外异常必须失败关闭。
- 数据库恢复只允许在已停止 frontend/backend 的维护窗口中执行。
- 回滚必须使用不可变上一 tag，禁止使用 `latest`。
