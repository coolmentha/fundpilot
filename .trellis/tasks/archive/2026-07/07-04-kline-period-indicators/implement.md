# Implement: K线周期切换修复+指标

## 执行顺序

### Step 1: 后端 chain 委托修复 (R1)
- [ ] `MarketDataSourceChain` override `fetchIndexKlineWithPeriod`:用 `tryEach` 委托到各 source(透传 klt)。
- [ ] `MarketDataSourceChainTest` 加用例:两 source,第一个抛异常,第二个收到正确 klt 并返回;断言 chain 透传 klt(102/103)而非走 default 日K。
- [ ] `mvn -q test -Dtest=MarketDataSourceChainTest` 验证

### Step 2: 后端 kline 重试 (R6)
- [ ] `EastmoneyMarketDataSource` 抽 `fetchWithRetry(Supplier<String>)`:try / catch RuntimeException / try once more;`fetchIndexKline` + `fetchIndexKlineWithPeriod` 复用。
- [ ] 单测:mock Feign 第一次抛 EOFException、第二次返数据 → 重试成功返 kline;两次都抛 → 抛。
- [ ] 编译验证

### Step 3: 前端换库 klinecharts (F1)
- [ ] `npm uninstall lightweight-charts && npm install klinecharts`
- [ ] 数据转换:后端 `KlineView.Bar`(ISO Instant + OHLCV)→ klinecharts `{timestamp: ms, open, high, low, close, volume}`。
- [ ] `npm run build` 验证依赖装好

### Step 4: 前端 KlineChart 重写 (F2)
- [ ] `KlineChart.jsx`:`init`/`dispose`,暗色 `setStyles`,蜡烛主图。
- [ ] `createIndicator('MA', true)` overlay MA5/10/20/30;`createIndicator('VOL')` 副图常驻;`createIndicator('MACD')` 副图开关。
- [ ] 工具栏:period Segmented + MA 开关 chip + 副图 Segmented(MACD/VOL/无)。
- [ ] period 变 → 重新拉数 `applyNewData`;MA/副图变 → create/removeIndicator;chartType 变 → 重建。
- [ ] chartType='nav' 保持单折线(用 klinecharts area/line),无工具栏无指标。
- [ ] `npm run build` 验证

### Step 5: 测试 + 文档
- [ ] `mvn test` 全绿
- [ ] `npm run build` 通过
- [ ] CONTEXT.md「行情数据缓存」或 K 线小节补:chain 委托 klt + 重试 + 前端算指标
- [ ] ADR 可选(若设计决策需留存):lightweight-charts v5 多 pane + 前端算指标

## 验证命令
```bash
cd backend && ./mvnw -q test -Dtest=MarketDataSourceChainTest,EastmoneyMarketDataSourceTest
cd backend && ./mvnw test
cd frontend && npm run build
```

## PR 与部署
- 分支:`feat/kline-period-indicators`
- tag:`v0.4.4`
