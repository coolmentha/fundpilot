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
