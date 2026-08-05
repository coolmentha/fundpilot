# 执行计划

## 1. 依赖和共享工具

- [x] 用现有 npm 包管理器安装 `echarts`，移除 `klinecharts`，同步 `package-lock.json`。
- [x] 添加最小共享图表工具：ECharts 模块注册、初始化/销毁、主题色读取、MA/MACD 和对称边界计算。

## 2. 今日分时

- [x] 用 ECharts line/area option 替换 KLineCharts 初始化和数据填充。
- [x] 保留交易段展开、午休跳过、未来 `null`、百分比/净值切换。
- [x] 实现显式对称 Y 轴和 0% 参考线，适配深色/亮色主题及 resize。
- [x] 更新 `FundIntradayChart.test.jsx`，断言 `setOption` 中的类目、未来空值和 `min/max` 对称。

## 3. K 线

- [x] 用 ECharts candlestick/line/bar 替换 KLineCharts 主图和副图生命周期。
- [x] 实现 MA、VOL、MACD/DIF/DEA option，保留现有工具栏状态和 NAV 面积图降级。
- [x] 保留加载、错误、空数据和 benchmark 展示；删除旧的 `window.onerror` 竞态抑制。
- [x] 新增 `KlineChart.test.jsx`，覆盖图表初始化、K 线数据顺序、MA/VOL/MACD/NAV 分支和销毁。

## 4. 样式和验证

- [x] 清理 KLineCharts 专属的宽度/高度假设，保持现有工具栏可换行和移动端容器约束。
- [x] 搜索确认生产源码、测试和锁文件无 `klinecharts` 残留。
- [x] 运行 `npm run lint`、`npm test`、`npm run build`。
- [ ] 启动 Vite，检查桌面和 320px 页面截图/像素，重点验证 0% 中线、未来无曲线、K 线副图和主题切换（浏览器连接器缺失，待环境恢复后补做）。
