## Bug Analysis: 提示型风险规则误入交易确认路径

### 1. Root Cause Category

- **Category**: B - Cross-Layer Contract, with C - Change Propagation Failure.
- **Specific Cause**: V20 将总资金池和单基金上限建模为确认交易的硬校验。设置页、基金字段、确认服务和错误码都围绕“阻断买入”实现；用户实际需要的是现金流和集中度的可见提示。因此
  `TRANSFER_IN` 在净值回填后的确认路径也被错误阻断。

### 2. Why Fixes Failed

1. 排查定时任务和净值回填没有解决问题：交易已有净值后仍保持 PENDING，说明阻断发生在确认服务的后置业务校验而不是调度或数据源。
2. 只删除单个错误提示不足：`totalCapital`、`PositionLimitService`、前端入金入口和文档仍会重新引入旧语义，必须同时替换数据模型、确认路径和
   UI 合同。

### 3. Prevention Mechanisms

| Priority | Mechanism        | Specific Action                                                                 | Status |
|----------|------------------|---------------------------------------------------------------------------------|--------|
| P0       | Architecture     | 月度预算与仓位提醒只由摘要/展示层读取；确认服务不得依赖它们。                   | DONE   |
| P0       | Regression tests | 覆盖 PENDING/CONFIRMED/CANCELLED 预算统计、14:55 边界、跨月顺延和无硬拦截确认。 | DONE   |
| P1       | Code-spec        | 在交易一致性规范明确提示规则不能抛确认错误。                                    | DONE   |
| P1       | API contract     | 用 `monthlyDcaBudget` 替换累计入金端点，所有前端变更统一失效预算摘要缓存。      | DONE   |

### 4. Systematic Expansion

- **Similar Issues**: 任何用户偏好、预警阈值或展示型风险指标都不应直接进入 `TransactionConfirmSupport`、`FundService`
  建仓或转换确认路径。
- **Design Improvement**: 未来新增风险提示时，应先定义“纯展示”还是“交易约束”；若是展示，服务输出摘要/View，不能新增
  `BusinessException`。
- **Process Improvement**: 涉及交易确认的产品改动必须沿 Storage -> Service -> API -> UI 和 PENDING -> CONFIRMED
  全路径复核，而不是只验证设置页。

### 5. Knowledge Capture

- [x] 更新 `backend/transaction-consistency.md` 的确认约束、错误矩阵和回归测试。
- [x] 更新业务文档和 ADR，标记 ADR-0020 已被替代。
- [x] 将月度预算摘要日期规则收敛到 `DcaScheduleService`。
