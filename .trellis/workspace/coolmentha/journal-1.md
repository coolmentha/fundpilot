# Journal - coolmentha (Part 1)

> AI development session journal
> Started: 2026-07-04

---



## Session 1: 修复 QDII 当日收益确认日期

**Date**: 2026-07-23
**Task**: 修复 QDII 当日收益确认日期
**Branch**: `hotfix/qdii-first-seen-daily-pnl`

### Summary

QDII 今日收益改为按最新净值 firstSeenAt 的北京时间当天结算，次日归零并保留最新净值用于市值和总盈亏；补齐单基金与批量回归测试及业务规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d5ac5d3` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: 基金详情当日分时图发布

**Date**: 2026-07-24
**Task**: 基金详情当日分时图发布
**Branch**: `main`

### Summary

同花顺分钟线接入实时缓存和基金详情 Tab，PR #124 通过 CI 并以 v0.5.103 部署。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `b9b76c7` | (see git log) |
| `a4906a5` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: 分时图指标切换

**Date**: 2026-07-24
**Task**: 分时图指标切换
**Branch**: `feature/fund-intraday-metric-toggle`

### Summary

基金今日分时默认展示相对基准净值的涨跌幅，支持切换净值，并完成前端验证。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `05188ca` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: 接入大盘涨停跌停数据

**Date**: 2026-07-24
**Task**: 接入大盘涨停跌停数据
**Branch**: `feature/market-limit-counts`

### Summary

接入同花顺动态 Cookie 会话，发布完整市场宽度快照并补充前端展示和回归测试。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `32af452` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: 修复基金详情分时完整交易时段并发布

**Date**: 2026-08-04
**Task**: 修复基金详情分时完整交易时段并发布
**Branch**: `main`

### Summary

透传同花顺交易时段，补齐前端完整交易分钟轴并保留午休空档与未来空槽；修复移动端 klinecharts 可视窗口。PR #204 合并至 main，v0.11.7 部署 run 30914667862 成功，线上移动端与桌面端确认 09:30-15:00，Grafana 入口保持有效。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `9fdc678` | (see git log) |
| `d192546` | (see git log) |
| `a17f2df` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: 部署后清理旧镜像并发布 v0.11.9

**Date**: 2026-08-05
**Task**: 部署后清理旧镜像并发布 v0.11.9
**Branch**: `main`

### Summary

为生产 deploy workflow 增加定向清理旧 FundPilot backend/frontend 镜像；保留当前 digest，清理失败只告警。通过 shell/YAML/逻辑验证后，经 PR #206 合入 main，发布 v0.11.9，Deploy run 30975497299 成功。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d35a36c` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: 统一基金图表并发布 v0.11.10

**Date**: 2026-08-05
**Task**: 统一基金图表并发布 v0.11.10
**Branch**: `main`

### Summary

基金详情页分时图和 K 线统一切换为 ECharts，分时百分比轴动态对称并将 0% 固定居中；新增图表测试和共享计算工具，移除 klinecharts。npm test、lint、build 及 GitHub CI 全部通过。PR #207 合并后从 main 合并提交创建并推送 v0.11.10，Deploy run 30987985630 完成镜像构建、VPS 健康检查和旧应用镜像清理。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `f607245` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 8: 完成 K 线初始窗口修复并部署

**Date**: 2026-08-06
**Task**: 完成 K 线初始窗口修复并部署
**Branch**: `main`

### Summary

修复日K、周K、月K首次显示范围与ECharts大数据模式问题；PR #209 合并到 main，v0.11.12 已通过 CI、镜像构建、VPS 部署和线上健康检查。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `77bc539` | (see git log) |
| `cea1864` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 9: 发布国外实时指数

**Date**: 2026-08-07
**Task**: 发布国外实时指数
**Branch**: `main`

### Summary

设置页新增纳斯达克、日经225、韩国KOSPI实时指数候选项；PR #211 已合并，v0.11.14 部署成功；main CI 31165570158 与 Deploy 31165945363 全部通过。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1238f18` | (see git log) |
| `c2c47e8` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 10: 完善后端异常日志并发布

**Date**: 2026-08-10
**Task**: 完善后端异常日志并发布
**Branch**: `hotfix/fund-estimate-error-logging`

### Summary

将后端仅打印异常 message 的日志统一改为记录完整 Throwable，保留业务上下文，并补充 backend logging 规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `aeb6056` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 11: 移除QDII盘中估值

**Date**: 2026-08-10
**Task**: 移除QDII盘中估值
**Branch**: `hotfix/disable-qdii-estimates`

### Summary

诊断生产估值失败，移除QDII盘中估值调用、扩展时段调度及旧估值分时缓存，保留确认净值收益规则。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `9744389` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 12: 优化行情工作台数据展示

**Date**: 2026-08-11
**Task**: 优化行情工作台数据展示
**Branch**: `feature/market-dashboard-data-refresh`

### Summary

补充市场状态与缓存时效，重构持仓贡献和行业表现展示，并完成前后端验证。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `60eb506` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 13: 完成智能定投

**Date**: 2026-08-12
**Task**: 完成智能定投
**Branch**: `feature/smart-dca`

### Summary

新增低估、均线、涨跌幅智能定投与固定模式，补充本地估值、执行留痕、预算区间和前端展示。

### Git Commits

| Hash | Message |
|------|---------|
| `4a7b1aa` | (see git log) |

### Testing

- [OK] 后端定向及架构测试 75 个通过
- [OK] 前端 103 个测试、lint、build 通过

### Status

[OK] **Completed**

### Next Steps

- 推送分支并通过 PR、main CI、v0.11.19 部署


## Session 14: 支持修改当前持仓成本价

**Date**: 2026-08-12
**Task**: 支持修改当前持仓成本价
**Branch**: `feature/smart-dca`

### Summary

新增当前持仓成本价修正能力，仅更新 Accounting Position，不修改历史交易或 FIFO lot；补齐收益查询、前端编辑与测试。

### Git Commits

| Hash | Message |
|------|---------|
| `14fb3f3` | (see git log) |

### Status

[OK] **Completed**


## Session 15: 修正智能定投涨跌幅净值口径

**Date**: 2026-08-13
**Task**: 修正智能定投涨跌幅净值口径
**Branch**: `feature/smart-dca`

### Summary

涨跌幅策略改为仅使用严格早于定投业务日的最近确认净值，并补充日期边界回归测试。

### Git Commits

| Hash | Message |
|------|---------|
| `a248b2d` | (see git log) |

### Status

[OK] **Completed**
