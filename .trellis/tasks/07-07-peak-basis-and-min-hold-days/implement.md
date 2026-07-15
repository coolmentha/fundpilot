# Implement: 峰值基准切换与最小持有期可配

## 执行顺序

严格按依赖关系串行，每步可独立验证。先 DB → entity → service → controller → frontend → 契约 → 测试。

### Phase 1: DB 迁移

- [ ] 1.1 新建 `backend/src/main/resources/db/migration/V15__add_peak_nav_basis_and_min_hold_days.sql`
    - `ALTER TABLE fund_strategy ADD COLUMN peak_nav_basis VARCHAR(32), ADD COLUMN min_hold_days INTEGER;`
    - `UPDATE fund_strategy SET peak_nav_basis='HOLDING_PERIOD', min_hold_days=5 WHERE peak_nav_basis IS NULL;`
    - 参照 V9 风格（现有最大版本为 V9，V10–V14 跳号，本任务用 V15）。

### Phase 2: 后端 entity + enum

- [ ] 2.1 新建 `backend/src/main/java/com/fundpilot/backend/strategy/enums/PeakNavBasis.java`
    - `implements EnumValue`（参照其他枚举如 `FundStatus` 的 EnumValue 实现方式）
    - 三值：HOLDING_PERIOD("持有期高点") / ROLLING_60D("滚动60日高点") / ALL_TIME("全历史前高")
- [ ] 2.2 `FundStrategyEntity` 增加 `peakNavBasis`、`minHoldDays` 字段（@Enumerated(STRING) + Integer）

### Phase 3: 后端 service

- [ ] 3.1 `SignalGenerationService.buildCapitalContext`（:135）按 `strategy.getPeakNavBasis()` 分派 trailing peak：
    - HOLDING_PERIOD/null → `findPeakAccumulatedNavSince(openedAt)`
    - ROLLING_60D → `findPeakAccumulatedNavSince(today.minus(60, ChronoUnit.DAYS))`
    - ALL_TIME → `findPeakAccumulatedNav(fundId)`
    - 注意 `today` 参数已传入 buildCapitalContext（现签名 Instant date），用它算 minus。
- [ ] 3.2 `DisciplineStrategyService`：
    - 删 `MIN_HOLD_DAYS = 5` 常量（:50）
    - `evaluateSignal` 从 `strategy.getMinHoldDays()` 读取，null→5，传入 `applyMinHoldDays`
    - `applyMinHoldDays` 签名增 `int minHoldDays` 参数
- [ ] 3.3 `StrategyConfigService` 校验：
    - `minHoldDays` 非 null 时必须 ∈ [1, 20]，否则抛 `STRATEGY_CONFIG_INVALID`（ErrorCode.java:21 已存在）
    - `peakNavBasis` 非 null 时必须是合法枚举值

### Phase 4: 后端 DTO

- [ ] 4.1 `StrategyConfigRequest` record 增 `PeakNavBasis peakNavBasis, Integer minHoldDays`
- [ ] 4.2 `FundStrategyView` record 增同两字段；`from(...)` 同步映射

### Phase 5: 前端

- [ ] 5.1 `frontend/src/constants.js`：加 `PEAK_NAV_BASIS_OPTIONS`
- [ ] 5.2 `frontend/src/pages/StrategyFormModal.jsx`：
    - 加"峰值基准"下拉（Form.Item name="peakNavBasis"，默认 HOLDING_PERIOD）
    - 加"最小持有期"数字输入（Form.Item name="minHoldDays"，默认 5，min=1 max=20）
    - onOk 把两字段并入 payload
- [ ] 5.3 `frontend/src/pages/FundStrategyTab.jsx`：策略列表/详情展示基准与窗口

### Phase 6: 契约文档

- [ ] 6.1 `CONTEXT.md`「持有期高点 holdingPeriodPeakNav」节：扩展说明三档基准（HOLDING_PERIOD/ROLLING_60D/ALL_TIME）
- [ ] 6.2 `CONTEXT.md`「7 天内不赎回硬约束 MIN_HOLD_DAYS」节：5 → "默认 5，可配 [1,20] 交易日"
- [ ] 6.3 判断是否需补 ADR（若 review 认为基准可切换超出 ADR-0001 边界）

### Phase 7: 测试

- [ ] 7.1 `DisciplineStrategyServiceTest`：
    - 新增 HOLDING_PERIOD baseline 用例（验证与现有行为逐位一致）
    - 新增 minHoldDays 自定义值用例（如 10 日窗口下未满降级 NONE、逻辑止损豁免）
    - 新增 minHoldDays=null fallback 到 5 用例
- [ ] 7.2 `StrategyConfigServiceTest`：
    - minHoldDays 越界（0、21、负数）抛 STRATEGY_CONFIG_INVALID
    - peakNavBasis 合法枚举通过
- [ ] 7.3 信号生成集成测试（若有）：覆盖 ROLLING_60D 基准派生（注意：SignalGenerationService 集成测试需 Postgres
  testcontainer，CI 跑）

## 验证命令

```bash
# 后端编译 + 单测
cd backend && ./mvnw compile && ./mvnw test -Dtest='DisciplineStrategyServiceTest,StrategyConfigServiceTest'

# 前端构建
cd frontend && npm run build
```

## 风险文件与回滚点

| 文件                                            | 风险                   | 回滚                                          |
|-----------------------------------------------|----------------------|---------------------------------------------|
| V15 迁移                                        | 触及 fund_strategy     | `DROP COLUMN peak_nav_basis, min_hold_days` |
| `DisciplineStrategyService`                   | 删常量改读字段，影响所有 SELL 路径 | revert commit；fallback null→5 保证存量不破        |
| `SignalGenerationService.buildCapitalContext` | 基准派生错误直接导致信号失真       | 单测覆盖三档 + null fallback                      |
| `CapitalContext` javadoc                      | 第二参数语义放宽             | 仅文档改动，无运行时影响                                |

## Follow-up 检查（task.py start 前）

- [ ] PRD 收敛 pass 已完成（无重复事实、无悬空 open question）
- [ ] design.md 权衡表完整
- [ ] V15 版本号为下一个可用迁移号（现有最大 V9，V10–V14 跳号）
- [ ] 用户已审阅 prd.md / design.md 或明确同意进入实现

## 未完成事项

- 若 review 后需补 ADR，在 Phase 6.3 决定。
- 集成测试（7.3）依赖 CI Postgres service container，本地未必能跑，CI 兜底。
