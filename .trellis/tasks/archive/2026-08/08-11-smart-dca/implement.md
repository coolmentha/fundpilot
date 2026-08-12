# 智能定投实施计划

## 前置门禁

- [ ] 用户审阅 `prd.md`、`design.md`、`implement.md` 并明确批准实现及 V49 schema 变更。
- [ ] 获得创建并切换 `feature/smart-dca` 的明确确认；从 `main` 创建，不带入或覆盖现有用户改动。
- [ ] 运行 `task.py start .trellis/tasks/08-11-smart-dca`，确认任务进入 `in_progress`。
- [ ] 为 implement/check 子代理配置真实 spec 与 research 上下文。

## 实施顺序

1. [ ] 新增 V49：计划策略列、`index_valuation`、智能执行记录表及约束/索引。
2. [ ] 扩展中证客户端、估值解析、持久化和 MarketData 公开 API，通过专用 `indexCsiDsPe` 接口首次全量、后续增量保存带来源的 PE 历史。
3. [ ] 扩展 InvestmentPlan 聚合、JPA 映射、仓储和 Web DTO；存量/缺省策略统一为 `FIXED`。
4. [ ] 扩展 PortfolioFund Gateway 返回产品与基准指数，并在创建/更新时校验低估和均线配置。
5. [ ] 实现单个纯 `SmartInvestmentAmountPolicy`，固化 `ALIPAY_2025_06_V1` 的三组档位和边界。
6. [ ] 实现本地事实 Gateway 与执行记录仓储；执行 Handler 接入智能计算、跳过留痕和双重幂等。
7. [ ] 让月计划执行判断与预测合并智能执行记录，确保跳过即视为本期已处理。
8. [ ] 扩展计划与预算查询：基础预测保持不变，增加范围和最近决策的批量读取。
9. [ ] 更新表单、计划列表和预算概览，默认固定金额，展示策略范围及最近决策原因。
10. [ ] 补齐单元、集成、迁移、前端交互和架构测试；不修改依赖清单。

## 重点测试

- [ ] `SmartInvestmentAmountPolicyTest` 覆盖三种策略所有边界、缺数据和两位金额舍入。
- [ ] `InvestmentPlanExecutionCommandHandlerTest` 覆盖固定路径不变、智能执行、智能跳过、同日重复和月计划跳过后不补投。
- [ ] `InvestmentPlanCommandHandlerTest` 覆盖模式参数、基准指数与均线周期校验。
- [ ] 中证估值解析与 `index_valuation` 持久化测试覆盖有效 PE、空响应、重复日期和增量 upsert。
- [ ] Web/Flyway 集成测试验证旧计划默认 `FIXED`、智能字段与执行记录唯一键。
- [ ] 预算测试验证主金额仍按基础金额，区间按策略上下限。
- [ ] 前端测试验证默认固定、模式切换、条件字段、请求体和最近决策文案。
- [ ] Spring Modulith/ArchUnit 测试验证新增跨模块依赖只经过公开 API。

## 验证命令

在 `backend/`：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\mvnw.cmd -B clean verify
```

在 `frontend/`：

```powershell
npm test
npm run lint
npm run build
```

## 审查与回滚点

- [ ] 首先审查 V49 的默认值、约束、索引和旧版本兼容性，再进入应用代码。
- [ ] MarketData PE 扩展通过后单独确认原 K 线与量能测试未回归。
- [ ] 后端完成后先检查固定计划行为，再接前端；任何固定路径差异均回退到设计阶段。
- [ ] 最终质量检查覆盖 backend、frontend、跨层字段流和完整迁移链。
- [ ] 不执行 commit、push、merge、tag 或部署；这些动作另行获得用户确认。
