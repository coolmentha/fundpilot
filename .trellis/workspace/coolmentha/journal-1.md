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
