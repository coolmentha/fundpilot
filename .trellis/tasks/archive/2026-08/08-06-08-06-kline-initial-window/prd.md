# K线按周期限制初始显示范围

## Goal

保留日K、周K、月K完整数据，但首次只显示最近窗口，避免全部历史触发 ECharts 大数据渲染并看不清蜡烛实体。

## Requirements

- 保留后端返回的完整日 K、周 K、月 K 数据，历史数据仍可通过 ECharts 缩放查看。
- K 线首次渲染只定位到最近窗口：日 K 120 根、周 K 104 根、月 K 60 根。
- 初始窗口中的 K 线必须保留完整的开收实体和上下影线，不进入 ECharts 大数据简化渲染。
- 日 K、周 K、月 K 切换后分别使用对应窗口，不改变 OHLCV 数据、MA、成交量、MACD 和 Tooltip 逻辑。
- 数据少于窗口时显示全部数据，不出现负索引或空白图表。
- 不修改后端接口、数据聚合、数据源和依赖。

## Acceptance Criteria

- [x] 初始 K 线 `dataZoom` 的 `startValue` 按 daily/weekly/monthly 分别定位到最近 120/104/60 根。
- [x] 数据不足对应窗口时从第 0 根开始显示。
- [x] 日/周/月切换、指标切换和现有 K 线数据顺序测试通过。
- [x] `npm run lint`、KlineChart 组件测试和 `npm run build` 通过。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
