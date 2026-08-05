# 技术设计

## 边界

只修改 `frontend`：两个图表组件、共享的图表计算/初始化工具、组件样式、测试和 npm 依赖。后端继续提供：

```text
Intraday: estimateDate + baseNav + points[{time, nav}] + tradingSessions[{start, end}]
Kline: chartType + benchmark + bars[{date, open, high, low, close, volume}]
```

## 图表实例

使用 ECharts core、必要组件和 Canvas renderer，在一个共享工具文件中完成模块注册和 `init/dispose` 包装。组件自身负责 option 生成和 React 状态，避免引入第三方 React wrapper。

实例生命周期：

1. 容器可用且 K 线 `chartType` 已知时初始化。
2. 数据、指标选择或主题变化时用 `setOption(..., {notMerge: true})` 重建当前 option，避免旧副图残留。
3. 监听 `window.resize`，并在可用时观察容器尺寸；清理监听器、ResizeObserver 和 ECharts 实例。

## 今日分时

先按后端交易段生成时间类目，再用 `Map(time, nav)` 填充数据。百分比值为：

```text
(nav / baseNav - 1) * 100
```

未来槽位为 `null`。百分比模式计算所有有效值的最大绝对值，向上取整到适合刻度后设置：

```text
yAxis.min = -bound
yAxis.max = +bound
```

`bound` 至少为 0.01 个百分点，因此无波动或单向上涨时 0% 仍位于中线；有实际波动时不人为扩大到 1%，而是按 1/2/5 刻度向上取整。Y 轴增加 `markLine`/axis pointer 的 0 参考线；ECharts 的显式边界禁止自动把轴变成单向范围。净值模式使用真实 NAV 的自动范围。

## K 线与指标

- `kline` 使用 `candlestick`，数据顺序为 `[open, close, low, high]`，红涨绿跌。
- MA 使用简单移动平均，周期来自当前勾选集合；不足周期的前置值为 `null`。
- VOL 使用独立 grid 的柱状图。
- MACD 使用 EMA12、EMA26、DEA9，柱值为 `2 * (DIF - DEA)`，独立 grid 同时绘制柱、DIF、DEA。
- `NONE` 只保留主图；`VOL`/`MACD` 复用第二 grid。
- `tooltip.trigger = axis`、`axisPointer.type = cross`，formatter 汇总当前 K 线、均线和副图指标。
- K 线启用 `dataZoom.inside`，日/周/月和 MA/副图切换都重新生成 option；不改变后端数据顺序。
- `nav` 使用 line + areaStyle，单一主 grid，无工具栏。

## 主题与响应式

从 `ThemeModeContext` 获取主题，颜色使用 `--color-*` 对应的现有深浅主题值，不在组件中固定深色背景。分时和 K 线共享网格、坐标轴、Tooltip 和十字线颜色配置。

分时使用 100% 容器宽度，类目轴标签按数据量自动稀疏；不再用 KLineCharts 的 `barSpace`/右侧偏移。K 线容器保持现有 420/520px 高度，移动端通过 ECharts inside dataZoom 保持可操作。

## 兼容与回滚

- API 契约和调用 Hooks 不变，后端可独立回滚。
- 若 ECharts 构建体积或浏览器兼容性不满足验收，可只回滚本分支的依赖和两个组件，不涉及数据迁移。
