# 修复 GitHub Issue #190-#195

## Goal

修复当前开放的 6 个后端 bug，确保作废组合基金不再进入行情和定投可见路径，相关业务拒绝返回稳定的业务错误，并让定投参数与状态校验遵循既有错误码契约。

## Requirements

- #190：行情侧 `OwnedFundProductGateway` 的 legacyFundId 和 portfolioFundId 两条入口只接受 `Validity.TRACKED`，作废组合基金不得触发产品查询、实时估值或 K 线查询。
- #191：定投计划相关的作废组合基金拒绝必须映射为 `BusinessException`，不能由裸 `RuntimeException` 进入 500 兜底。
- #192：策略管理相关的作废组合基金拒绝必须映射为 `BusinessException`，查询和全部命令入口保持一致。
- #193：建议 latest/range 查询遇到作废组合基金时返回稳定业务错误，不再抛自定义裸运行时异常；只读接口保持现有响应结构。
- #194：定投金额、频率、周/月计划日等非法输入统一返回 `DCA_PLAN_INVALID`；DRAFT 计划不允许的退休、暂停/恢复操作返回 `ILLEGAL_STATE_TRANSITION`。
- #195：全局定投计划列表与预算摘要共用同一套“关联组合基金仍为 TRACKED”的可见计划集合；作废计划保留数据库审计，但从默认列表和预算预测排除。
- 保持现有 API 结构和持久化语义，不新增数据库迁移、不升级依赖、不删除历史数据。

## Acceptance Criteria

- [x] #190 的两个行情网关入口对 VOIDED 返回空结果，并补充单测证明不会读取产品。
- [x] #191/#192/#193 的作废基金入口抛出 `BusinessException`，错误码为稳定的 `ILLEGAL_STATE_TRANSITION`，全局处理器返回 HTTP 400。
- [x] #194 的金额、周计划日、月计划日、DRAFT 退休及启停边界均返回约定错误码，且不保存非法变更。
- [x] #195 的全局列表和预算摘要均排除 VOIDED 关联计划，并有可见计划过滤回归测试。
- [ ] 相关后端定向测试、全量测试、格式/编译检查通过；定向测试、构建、空白检查已通过，全量数据库/容器测试等待本机 PostgreSQL/Docker 可用；代码中不残留本批次调试输出。

## Notes

- #193 采用统一业务拒绝而不是空列表，避免把“基金不可用”和“当前没有建议”混为同一语义；latest/range 的现有 DTO 结构不变。
- 当前任务只处理开放 issue #190-#195，不关闭 GitHub issue、不提交、不推送。
