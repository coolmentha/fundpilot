# Implement: 定投止盈类型推荐

## 执行顺序

### 1. 领域与数据库

- [x] 新增 `TakeProfitPhase` 枚举。
- [x] 扩展 `FundStrategyEntity` 配置字段和运行时字段。
- [x] 新增 V15 迁移，按基金类型回填推荐配置并将存量策略标记为自定义。
- [x] 扩展 Request/View 和 `ErrorCode`，加入完整参数校验。
- [x] 添加迁移/Repository/StrategyConfigService 测试。

验证：`mvn -q -DskipTests compile`。

### 2. 推荐服务与 API

- [x] 新增 `TakeProfitPresetService` 和推荐值对象，集中维护四类模板。
- [x] 新增 recommendation View 与 GET 端点。
- [x] 新建策略缺省时应用推荐；保存时由服务端计算 `customized`。
- [x] 添加四类型推荐、用户覆盖和恢复后重新匹配的测试。

验证：运行策略配置相关测试。

### 3. 定投止盈生命周期与信号计算

- [x] 新增 `TakeProfitLifecycleService`，实现 ACCUMULATING/ARMED/TRIGGERED/COOLDOWN 状态机。
- [x] 基于 `fund_lot` + 交易日历计算成熟可赎回份额，包含未跟踪调整份额降级。
- [x] 扩展 `CapitalContext`，按整仓收益和四项上限计算建议卖出份额。
- [x] 保持逻辑止损最高优先级，移除旧的线性四档重复卖出语义。
- [x] `SignalGenerationService` 绑定本周期唯一止盈信号。
- [x] 两条确认路径和撤单路径统一推进/恢复止盈状态。
- [x] 覆盖启动当天不卖、创新高、回撤触发、重复生成、PENDING、确认、撤销、冷静期直接再开、定投新 lot 不锁旧份额等测试。

验证：运行 strategy/signal/fund transaction 相关测试。

### 4. 前端推荐与编辑

- [x] 新增 recommendation hook。
- [x] 重构 `StrategyFormModal`：推荐预填、正数百分比、边界校验、恢复推荐值、自定义提示和规则摘要。
- [x] 扩展 `FundStrategyTab` 列表/当前策略摘要和激活确认。
- [x] 将 `PENDING_CALIBRATION` 标签改为“草稿”。

验证：`npm run build`。

### 5. 文档与全量验证

- [x] 更新 `CONTEXT.md` 定投止盈领域定义。
- [x] 新增 ADR，记录类型推荐、整仓盈利启动、lot 保护和周期状态机。
- [x] 更新 `.trellis/spec/backend/transaction-consistency.md` 的确认/撤单联动契约。
- [x] 运行 backend 全量测试。
- [x] 运行 frontend build。
- [x] 运行 `trellis-check`，核对 PRD AC1-AC10、跨层数据流和无关改动。

## 验证命令

```powershell
cd backend
mvn test

cd ..\frontend
npm run build

cd ..
python ./.trellis/scripts/task.py validate 07-10-dca-take-profit-presets
```

## 风险文件与回滚点

- `backend/src/main/resources/db/migration/V15__*.sql`：仅允许加列和可逆语义回填，不删历史数据。
- `SignalGenerationService`：同日覆盖逻辑和跨日未回应信号必须保持可读。
- `NavConfirmService` / `TransactionConfirmService` / `TransactionCancelService`：只增加生命周期委托，不复制手续费或状态逻辑。
- `StrategyFormModal.jsx`：编辑值优先于推荐值，异步 recommendation 不得覆盖用户已输入内容。
