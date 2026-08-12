# 允许用户修改持仓成本价 - 实施计划

## 实施步骤

- [x] 1. 在 Accounting Position 聚合新增“修正当前成本单价”行为，保持状态、份额和建仓时间不变，并补最小领域测试。
- [x] 2. 扩展现有 PortfolioCorrection 用例及公开 adapter API：校验所有权、有效持仓和正数成本，按既有锁顺序保存 Position，并映射稳定错误码。
- [x] 3. 在 `FundService.update` 中仅当请求携带 `costPerShare` 时调用 Accounting 修正 API；旧调用方不传字段时维持原行为。
- [x] 4. 在 `FundsPage` 编辑态回填并展示成本单价，复用现有 InputNumber 规则和保存动作；空仓及已清仓不展示。
- [x] 5. 补后端集成测试和前端组件测试，覆盖成功修改、非法值、非持仓、越权、lot 不变及查询刷新。PostgreSQL 集成测试已编写，当前本机数据库未启动，尚未执行。
- [x] 6. 更新领域词汇与业务流程文档，明确只改当前成本、不改历史交易和 FIFO lot。
- [x] 7. 运行可执行的目标测试、质量门禁并审查 diff，确认未混入现有工作区改动；依赖 PostgreSQL 的集成测试保留为环境阻塞。

## 验证命令

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
Set-Location backend
.\mvnw.cmd -B -Dtest=PositionTest,PortfolioCorrectionIntegrationTest,FundServiceTest test
.\mvnw.cmd -B verify

Set-Location ..\frontend
npm test -- src/pages/FundsPage.test.jsx
npm run lint
npm test
npm run build
```

## 风险与回滚点

- 并发买入确认与成本修正必须使用一致锁顺序；目标测试需证明最终成本不会静默丢失。
- `FundCreateRequest.costPerShare` 同时服务新建和更新，必须保留 null 的“未修改”语义，避免旧编辑请求清空成本。
- 只修改任务范围内文件；不覆盖当前 21 项既有未提交改动。
- 不执行 schema、依赖、CI/CD 或 Git 写操作。
