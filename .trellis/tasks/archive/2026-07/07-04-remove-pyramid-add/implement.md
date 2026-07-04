# Implement: 移除金字塔加仓机制,移动止盈解耦为独立卖出纪律

## 执行顺序

按"后端核心 → 后端实体/DB → 后端回测/寻优清理 → 前端 → 测试 → 文档"顺序,每步可独立编译验证。

### Step 1: 后端信号引擎解耦(核心)
- [ ] `CapitalContext.java`:删 6 字段(plannedTotalAmount/buildShares/tierAddShares/singlePositionPct/categoryPositionPct/totalEquityAmount),改 record 签名 + javadoc
- [ ] `DisciplineStrategyService.java`:
  - 删 `decideBuild` / `decideAdd` / `clearTiersOnRebound` / `addWarnings` / `applyHardConstraints` / `computeCoefficient` / `toYearLineState` / tier 访问辅助(tierAddedAt/setTierAddedAt/tierDrawdown/tierRatio)
  - `decideAction`:PENDING_HOLDING → NONE(NO_STRATEGY);HOLDING → 只 decideSell
  - `checkTrailingStop` 重写:holdingShares × (triggerTier/4)
  - `checkLogicBrokenStopLoss` ACTIVE 分支:去掉 weeklyCoolDown 条件
  - `evaluateSignal` 步骤注释更新(九步→简化)
- [ ] `SignalGenerationService.java`:
  - `buildCapitalContext`:删 plannedTotalAmount/buildShares/tierAddShares/singlePositionPct/categoryPositionPct/totalEquityAmount 构造;CapitalContext 4 字段
  - 删 `buildTierAddShares` / `computeTotalEquityAmount` / `computeCategoryPositionPct`
  - `computeLastBuyConfirmTime`:只取最近 CONFIRMED 交易 confirmTime(删 tier1~4AddedAt 循环)
- [ ] 编译验证:`mvn -q compile`

### Step 2: 后端实体/DTO/Service
- [ ] `FundEntity.java`:删 plannedTotalAmount 字段
- [ ] `FundCreateRequest.java`:删 plannedTotalAmount(record 组件 + 工厂方法)
- [ ] `FundView.java`:删 plannedTotalAmount
- [ ] `FundService.java`:create/update 删 plannedTotalAmount 赋值(line 79, 194-195)
- [ ] `FundStrategyEntity.java`:删 tier1~4Drawdown/Ratio/AddedAt(12字段)+ weeklyCoolDownThreshold;留 status + stopLossPullbackPercent
- [ ] `FundStrategyView.java`:同步删字段
- [ ] `StrategyConfigRequest.java`:删 tier/weeklyCoolDown 字段,只剩 stopLossPullbackPercent
- [ ] `StrategyConfigService.applyRequest`:删 tier/weeklyCoolDown set
- [ ] 编译验证:`mvn -q compile`

### Step 3: 后端回测/寻优/硬约束清理
- [ ] 删文件:`DefaultStrategyBacktestService` / `StrategyOptimizeService` / `BacktestSimulator` / `BenchmarkCalculator` / `BacktestParams` / `OptimizeParamRanker` / `OptimizeGridGenerator` / `OptimizeParams` / `DefaultTierTable` / `DefaultCoolDownTable` / `TierDefaults` / `HardConstraintChecker` / `HardConstraintConfig` / `CoefficientTable` / `CoefficientCombiner`
- [ ] `StrategyConfigService`:删 `calibrate` 方法;createDraft 后流转简化(PENDING_CALIBRATION → 手动 activate → EFFECTIVE)
- [ ] 检查 `StrategyConfigController` 是否引用 calibrate/backtest/optimize 接口,删对应端点
- [ ] grep 残留引用:`grep -rn "BacktestService\|OptimizeService\|HardConstraint\|CoefficientTable\|DefaultTierTable\|DefaultCoolDownTable\|TierDefaults" backend/src/main/java` 应为空
- [ ] 编译验证:`mvn -q compile`

### Step 4: DB 迁移
- [ ] 新建 `V10__drop_pyramid_add_columns.sql`(见 design.md)
- [ ] 确认 V1~V9 不动

### Step 5: 前端
- [ ] `FundsPage.jsx`:删 plannedTotalAmount 表单项(line 217-223)/列(112)/初始值(15)/回填(63)
- [ ] `FundDetailPage.jsx`:删计划仓位 Descriptions.Item(50-52)
- [ ] `DashboardPage.jsx`:删计划仓位列(44-45)
- [ ] `FundStrategyTab.jsx`:删 tier 档位卡、回测对照列、校准按钮
- [ ] `StrategyFormModal.jsx`:FIELDS 只剩 stopLossPullbackPercent
- [ ] 构建验证:`npm run build`

### Step 6: 测试修复
- [ ] `FundServiceTest`:删 plannedTotalAmount 断言/构造参数
- [ ] `DisciplineStrategyServiceTest`:删 BUILD/ADD 用例;改移动止盈用例为 holdingShares×n/4;CapitalContext 构造改 4 字段
- [ ] `DefaultStrategyBacktestServiceTest` / `BenchmarkCalculatorTest` / `HardConstraintCheckerTest` / `HardConstraintConfigTest`:删除(被测类已删)
- [ ] `SignalGenerationServiceTest`:删 userConfigRepository/plannedTotalAmount 相关;CapitalContext 4 字段
- [ ] `FundServiceAutoFetchTest`:setUp 不再插 plannedTotalAmount
- [ ] grep 残留:`grep -rn "plannedTotalAmount\|tierAddShares\|buildShares\|singlePositionPct\|categoryPositionPct\|weeklyCoolDown\|HardConstraint\|BacktestSimulator\|OptimizeParam" backend/src/test` 应为空
- [ ] `mvn -q test`(本地无 DB,集成测试失败是预期;单元测试应过)

### Step 7: 文档
- [ ] CONTEXT.md:删金字塔加仓/计划仓位校验/再平衡/硬约束/BUILD/ADD 信号章节
- [ ] 新增 `docs/adr/ADR-0015-金字塔退场移动止盈解耦.md`
- [ ] `docs/PRODUCT.md`:删金字塔相关公式/表格

## 验证命令
```bash
cd backend && ./mvnw -q compile           # 编译
cd backend && ./mvnw -q test -Dtest='!*IntegrationTest'  # 单元测试(跳过需DB的)
cd frontend && npm run build              # 前端构建
```

## 风险点与回滚
- V10 DROP COLUMN 不可逆,PR review 重点确认。
- Step 3 删文件多,grep 残留引用是关键验证。
- 测试失败先看是否 CapitalContext 构造参数数不对(10→4)。

## PR 与部署
- 分支:`refactor/remove-pyramid-add`
- PR 标题:`refactor: 移除金字塔加仓机制,移动止盈解耦为独立卖出纪律`
- tag:`v0.4.0`(主版本号,策略机制级改动)
