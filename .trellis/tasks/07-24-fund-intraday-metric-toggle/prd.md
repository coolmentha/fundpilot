# 基金今日分时图指标切换

## Goal

基金详情的“今日分时”支持在净值与当日涨跌幅之间切换，默认展示当日涨跌幅。

## Requirements

- 今日分时提供“涨跌幅”和“净值”两个展示指标，默认选择“涨跌幅”。
- 涨跌幅基于接口返回的当日基准净值 `baseNav` 与每个分钟点净值计算：`(nav / baseNav - 1) * 100`。
- 切换仅改变图表展示与纵轴格式，不请求额外接口、不改变分钟点数据。
- 沿用现有数据有效性与空态：少于两个点时不展示指标切换器，显示“暂无当日分时数据”。
- 不改动后端接口、行情缓存或“今日涨跌”降级语义；不新增依赖。

## Acceptance Criteria

- [x] 首次打开“今日分时”时默认显示涨跌幅曲线与百分比纵轴。
- [x] 用户可切换为净值曲线，纵轴显示净值数值；再切回涨跌幅时结果正确。
- [x] 涨跌幅以 `baseNav` 为 0% 基准，任一点计算值正确。
- [x] 空态和既有 K 线 / 净值走势图 Tab 行为保持不变。
- [x] 前端自动化测试覆盖默认指标、切换行为和数据转换。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
