# Implement: 定投配置

## 执行顺序

### Step 1: 后端实体 + DB 迁移
- [x] `DcaFrequency.java`(WEEKLY/MONTHLY 枚举)
- [x] `DcaPlanStatus.java`(DRAFT/EFFECTIVE 枚举)
- [x] `FundDcaPlanEntity.java`(镜像 FundStrategyEntity,fundEntity/enabled/amount/frequency/dayOfWeek/dayOfMonth/status)
- [x] `FundDcaPlanRepository.java`(findByFundEntity_Id/findByFundEntity_IdAndStatus/findEffectiveIds)
- [x] `FundDcaPlanView.java`(DTO)
- [x] `DcaPlanRequest.java`(record: amount/frequency/dayOfWeek/dayOfMonth)
- [x] `V11__add_dca_plan.sql`(建 fund_dca_plan 表 + fund_transaction 加 dca_plan_id 列)
- [x] `FundTransactionEntity.java` 加 dcaPlanId 字段
- [x] 编译验证:`mvn -q compile`

### Step 2: 后端 Service + Controller
- [x] `DcaPlanService.java`(createDraft/updateDraft/activate/retire/listByFund/findActive,镜像 StrategyConfigService)
- [x] `DcaPlanController.java`(POST/PUT/GET/activate/retire 端点)
- [x] 编译验证

### Step 3: 后端 DcaSuggestionJob
- [x] `DcaSuggestionJob.java`(cron `0 55 14 * * MON-FRI`)
- [x] `DcaSuggestionJob.generateForFund(fundId, now)`:遍历 EFFECTIVE 计划 → 判定定投日 → 幂等去重 → 生成 PENDING INVEST 交易
- [x] 月定投顺延判定内联(planDom..today-1 全非交易日则今天补);交易日历查询统一 UTC 0 点
- [x] 编译验证

### Step 4: NavConfirmJob 时序调整
- [x] `NavConfirmJob.java` cron `0 0 21 * * MON-FRI` → `0 0 3 * * MON-FRI`
- [x] 确认 NavConfirmService 逻辑无需改(查 PENDING → 下单日净值 → 算 shares)
- [x] 编译验证

### Step 5: 前端
- [x] `hooks.js` 加 useDcaPlans/useActiveDcaPlan/useCreateDcaPlan/useUpdateDcaPlan/useDcaPlanAction
- [x] `DcaPlanFormModal.jsx`(amount/frequency/dayOfWeek/dayOfMonth 表单)
- [x] `FundDcaTab.jsx`(列表+新建/编辑/激活/停用,拷贝 FundStrategyTab 结构)
- [x] `FundDetailPage.jsx` items 加第 5 项定投计划 tab
- [x] `npm run build` 验证

### Step 6: 测试
- [x] `DcaPlanServiceTest`(CRUD + 状态机)
- [x] `DcaSuggestionJobTest`(周定投/月定投/节假日顺延/幂等去重/enabled=false)
- [x] 修复 Job 时区 bug(isDcaDay/run 查日历改用 UTC 0 点,对齐 InstantDateConverter 约定)
- [x] `mvn test` 验证(253 tests pass)

### Step 7: 文档
- [x] CONTEXT.md 加"定投计划"章节
- [x] ADR-0016 记录"定投配置:自动买入非信号,止盈交移动止盈"

## 验证命令
```bash
cd backend && ./mvnw -q compile
cd backend && ./mvnw clean test-compile
cd frontend && npm run build
```

## PR 与部署
- 分支:`feat/dca-config`
- tag:`v0.4.1`(功能新增,非破坏性)
