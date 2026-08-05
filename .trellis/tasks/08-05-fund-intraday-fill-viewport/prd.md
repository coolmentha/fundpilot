# 修复基金分时图时间轴铺满

## Goal

让基金今日分时图从 09:30 均匀铺满到 15:00，保留午休省略、未来槽空白和移动端可滚动布局。

## Requirements

- 有交易段时，分时图的分钟槽按实际绘图区宽度动态设置 `barSpace`，从首个 `09:30` 槽铺到末个 `15:00` 槽。
- 午休区间继续不生成槽位；交易段结束后的未来槽继续只保留时间戳，不绘制价格。
- 移动端绘图区仍至少容纳全部分钟槽和右轴宽度，必要时允许横向滚动；最小 `barSpace` 保持为 1。
- 窗口尺寸变化后重新计算间距，不改变百分比/NAV 数据和基准计算。
- 不修改后端交易时段、估值、今日盈亏或缓存契约。

## Acceptance Criteria

- [ ] 桌面宽度下，首个有效槽为 `09:30`，末个槽为 `15:00`，曲线横向铺满绘图区而不是集中在左侧。
- [ ] `09:30-11:30,13:00-15:00` 仍生成 242 个槽，午休不占槽位，未来槽价格为空。
- [ ] 移动端 `barSpace` 不小于 1，图表可横向滚动到完整交易段，桌面端不产生无意义的大片空白。
- [ ] 组件回归测试覆盖动态间距和窗口 resize；现有前端测试、lint、build 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
