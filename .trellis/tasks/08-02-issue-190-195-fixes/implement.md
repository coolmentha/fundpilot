# 实施计划

## 1. 建立失败反馈回路

- [x] 读取 #190-#195 正文、项目规范和现有实现。
- [x] 运行基线定向测试，确认现有测试通过但未覆盖本批次回归。
- [x] 先补充本批次回归测试并观察失败，再修改生产代码。

## 2. 生产代码改动

- [x] 在 `OwnedFundProductGatewayImpl` 统一过滤 VOIDED 组合基金。
- [x] 将定投、策略、建议 PortfolioFund gateway 的拒绝转换为 `BusinessException`。
- [x] 添加 `InvestmentPlanVisibleQueryHandler`，并让计划列表、预算摘要复用。
- [x] 在 `InvestmentPlanCommandHandler` 转换参数和非法状态异常。

## 3. 验证

- [x] 运行新增和受影响的定向单测，确认先红后绿。
- [ ] 运行 backend 全量 Maven 测试；已执行，但 80 个数据库/容器用例因 `localhost:5432` 与 Docker 不可用而阻塞。
- [x] 运行 `git diff --check`、编译/静态检查，并检查是否有调试输出。
- [x] 逐项记录 #190-#195 的代码和测试证据，不执行 issue 写操作。

## 4. 完成门禁

- [x] 运行 Trellis quality check。
- [x] 更新 `.trellis/spec/backend/transaction-consistency.md`，记录当前 DDD 可见性与业务错误契约。
- [x] 不执行 commit/push，等待用户后续指示。
