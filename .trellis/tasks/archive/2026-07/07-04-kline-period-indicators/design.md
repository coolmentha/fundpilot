# Design: K线周期切换修复+指标

## 根因分析

### 周期都一样 (R1)
`KlineService` 正确把 period→klt(101/102/103)传给 `marketDataSource.fetchIndexKlineWithPeriod(secid, klt, lmt)`。
但 `marketDataSource` 是 `MarketDataSourceChain`,它**没有 override `fetchIndexKlineWithPeriod`**,
继承了 `MarketDataSource` 接口的 default 实现 → `return fetchIndexKline(indexCode, "6")`(忽略 klt,恒为日K)。
`EastmoneyMarketDataSource.fetchIndexKline` 又调 2 参 `fetchKlineRaw(secid, lmt)`(Feign 硬编码 klt=101)。
所以无论前端选日/周/月,后端恒返日K。**这是核心 bug。**

### 降级净值走势 (R6)
secid `2.930713`(930713.CSI 中证人工智能)经 curl 验证**可用**(返 klines 数组)。生产 "Unexpected end of file from server"
是 push2his 偶发连接中断;Feign `Retryer.Default(100,1000,0)` = 0 次重试,瞬时失败即抛 → 降级净值。
同花顺 `ThsJsParser.parseIndexKline` 抛 UnsupportedOperationException(API 路径/格式未对接),非真实兜底。

### 指标缺失 (R2-R5)
前端 `KlineChart.jsx` 只有蜡烛+成交量柱,无 MA/MACD。降级净值时 volume=0 无成交量。

## 方案

### 后端

**B1. `MarketDataSourceChain` override `fetchIndexKlineWithPeriod`**
```java
@Override
public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
    return tryEach("fetchIndexKlineWithPeriod", indexCode,
            source -> source.fetchIndexKlineWithPeriod(indexCode, klt, lmt));
}
```
让 klt 透传到各 source 的 `fetchIndexKlineWithPeriod`。这是 R1 的修复。

**B2. `EastmoneyMarketDataSource.fetchIndexKlineWithPeriod` 重试一次**
```java
public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
    String raw = fetchWithRetry(() -> eastmoneyKlineClient.fetchKlineRaw(indexCode, klt, lmt));
    return EastmoneyJsParser.parseIndexKline(raw);
}
// fetchWithRetry:try / catch RuntimeException / try once more;仍失败抛
```
瞬时 EOF 重试一次。`fetchIndexKline`(日K 路径)也复用同一 retry helper。

**B3. `KlineService` 不动**——已正确传 klt,B1 修好后周期即生效。

### 前端

**图表库:换 klinecharts v9**(内置 MA/MACD/VOL/KDJ/RSI 等指标 + 指标切换,最像支付宝/券商 App)。
卸载 lightweight-charts,装 `klinecharts`。`FundMarketTab` 用法不变(仍 `<KlineChart fundId fundSubType/>`)。

**F1. 数据格式转换**
后端 `KlineView.Bar(date=ISO Instant, open, close, high, low, volume)` → klinecharts `{timestamp: ms, open, high, low, close, volume}`。
`timestamp = new Date(iso).getTime()`(klinecharts 用毫秒)。

**F2. 重写 `KlineChart.jsx`**
- `init(container)` 建图,`dispose()` 销毁。
- `setStyles(darkThemeOverrides)`:暗色背景 `#1E293B`,A 股红涨 `#EF4444` 绿跌 `#22C55E`,网格 `#334155`。
- 主图 pane:`createIndicator('CANDAL', ...)` 或默认蜡烛 + `createIndicator('MA', true)`(overlay MA5/MA10/MA20/MA30)。
- 副图 pane:`createIndicator('VOL')`(成交量,常驻)+ `createIndicator('MACD')`(开关)。
- `applyNewData(data)` 填充;period 变化时重新 `useFundKline(fundId, period)` 拉数 + `applyNewData`。
- **工具栏**:`Segmented` 日/周/月 + chip「MA」开关 + `Segmented` 副图(MACD/VOL/无)。
  - MA 开关:`createIndicator('MA', true)` / `removeIndicator(paneId, 'MA')`。
  - 副图切换:加/移 MACD、VOL。
- chartType='nav'(主动/混合/降级):单折线,用 `createIndicator('LINE'?)` 或直接自定义 series;klinecharts 也可画 line。简化:nav 模式仍用 lightweight-charts?不——统一 klinecharts,nav 用 `applyNewData` + 只显示 close 折线(隐藏蜡烛 OHLC,用 line 指标或自定义主图类型)。首版:nav 模式用 klinecharts 的 area/line,无工具栏无指标。

**F3. 重建时机**:period 变化 → 重新拉数 + `applyNewData`(不重建 chart);MA/副图切换 → `create/removeIndicator`(不重建);chartType 变化(kline↔nav)→ 重建 chart(主图类型不同)。

### 数据契约不变
`KlineView.Bar(date, open, close, high, low, volume)` 已含 OHLCV,前端算指标,后端不改 DTO。
`chartType`: 'kline'(指数/ETF) | 'nav'(主动/混合/降级)。

## 权衡

- **图表库**:用 klinecharts v9(用户选定)。内置 MA/MACD/VOL/KDJ/RSI + 指标切换 UI,最像支付宝;代价是换掉 lightweight-charts、学新 API、加依赖。
- **前端算指标 vs 后端算**:klinecharts 内置指标计算,前端只喂 OHLCV,无需自算;后端不改 DTO。
- **重试一次 vs 多次**:一次足矣(瞬时 EOF);多次可能触发 eastmoney 限流。
- **同花顺兜底**:descoped。其 API 路径/格式未对接(ThsClient 全 TODO),实现需调研,本期不做。retry-once 已覆盖瞬时失败主因。

## 兼容性

- `MarketDataSource` 接口 default `fetchIndexKlineWithPeriod` 保留(非 v5 数据源降级日K)。
- 前端 nav 模式行为完全不变(单折线,无工具栏无指标)。
- 无 DB schema 变更。前端依赖:卸 `lightweight-charts`,加 `klinecharts`。
