# 成本基准重置实施计划

## 实施步骤

- [x] 1. 扩展 Accounting 交易来源与聚合，支持创建即确认、零份额方向的 `COST_BASIS_RESET`，
  使用现有 `amount + shares` 保存重置总成本和份额快照，并拒绝普通交易入口伪造该来源。
- [x] 2. 在 PortfolioCorrection 同一锁和事务内写入成本重置流水与 Position 当前成本，补充失败回滚和持仓快照校验。
- [x] 3. 在 `LedgerReplay` 增加最近重置点后的成本重放，并由买入确认统一使用；保留无重置存量持仓的旧增量路径。
- [x] 4. 同步 Accounting API、legacy 枚举、份额方向、未跟踪份额和一次性 rebuild 对新来源的兼容处理。
- [x] 5. 在交易流水中显示“成本修正”、总成本、快照份额和派生成本价；保持该来源不出现在手动录入选项，
  成本保存后补充交易流水查询失效。
- [x] 6. 更新 ADR-0013、交易账务业务文档和相关代码注释，使成本基准重置与后续加权规则一致。
- [x] 7. 补最小回归测试，覆盖重置后买入、较早 PENDING 后确认、多次重置、调增边界、流水原子写入、
  普通入口拒绝、历史展示及无重置兼容。
- [ ] 8. 运行目标测试、完整质量门禁和 `git diff --check`，审查新来源所有生产调用路径与回滚风险。

验证记录：后端目标单测 21 项、前端全量 121 项、lint、build、`git diff --check` 均通过；
后端完整 `mvn verify` 编译通过并执行 554 项测试，84 项集成测试因本机 PostgreSQL 5432 未启动且
Docker Desktop 不可用而报环境错误（0 项断言失败），因此本步骤保持未完成。

## 重点文件

- `backend/src/main/java/com/fundpilot/backend/accounting/domain/transaction/TransactionSource.java`
- `backend/src/main/java/com/fundpilot/backend/accounting/domain/transaction/LedgerTransaction.java`
- `backend/src/main/java/com/fundpilot/backend/accounting/domain/ledgerreplay/LedgerReplay.java`
- `backend/src/main/java/com/fundpilot/backend/accounting/application/command/positiontracking/PositionCommandHandler.java`
- `backend/src/main/java/com/fundpilot/backend/accounting/application/command/portfoliocorrection/PortfolioCorrectionCommandHandler.java`
- `backend/src/main/java/com/fundpilot/backend/accounting/application/command/transactionledger/TransactionLedgerCommandHandler.java`
- `backend/src/main/java/com/fundpilot/backend/fund/enums/FundTransactionSource.java`
- `backend/src/main/java/com/fundpilot/backend/fund/service/FundPositionService.java`
- `backend/src/main/java/com/fundpilot/backend/fund/service/AccountingRebuildService.java`
- `frontend/src/pages/FundTransactionTab.jsx`
- `frontend/src/constants.js`
- `frontend/src/api/hooks.js`

## 验证命令

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"

Set-Location backend
.\mvnw.cmd -B '-Dtest=LedgerReplayTest,PositionCommandHandlerTest,PortfolioCorrectionCommandHandlerTest,PortfolioCorrectionIntegrationTest' test
.\mvnw.cmd -B verify

Set-Location ..\frontend
npm test -- src/pages/FundTransactionTab.test.jsx src/pages/FundsPage.test.jsx
npm run lint
npm test
npm run build

Set-Location ..
git diff --check
```

## 风险与回滚点

- `COST_BASIS_RESET` 必须在所有生产枚举和 switch 中处理为零份额方向；遗漏会导致查询失败或份额污染。
- 成本重放只在存在重置记录时启用，防止上线前无法识别的手工成本被全历史重算覆盖。
- 写流水与写 Position 必须保持同一事务和 PortfolioFund 锁顺序，避免并发确认覆盖。
- 回滚旧版本前按 `design.md` 软删除新来源行；不执行自动数据删除或不可恢复清理。
- 只修改本任务文件，保留未跟踪的 `.codex/skills/ui-ux-pro-max/scripts/__pycache__/`。
