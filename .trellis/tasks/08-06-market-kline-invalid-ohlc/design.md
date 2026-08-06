# 技术设计

## 数据流

`csindex.com.cn` 响应 → `CsindexJsParser` → `MarketDataSourceChain` →
`MarketIndicatorRefreshCommandHandler` → `index_kline` → K 线查询 → `KlineChart`。

当前解析器直接读取四个 OHLC 字段。中证接口在部分历史区间返回 `open/high/low=0`，但 `tradingVol` 仍有值；这些行被正常 upsert，前端 ECharts 的价格轴因此包含 0，主图被挤到顶部。

## 修复点

在 `CsindexJsParser.parseIndexKline` 构造 `IndexKline.Bar` 前读取四个 OHLC 值，并跳过任一值小于等于 0 的行。缺失字段由 Jackson 节点解析为 0 时同样被过滤。

- 混合有效/非法响应：返回有效行，避免一个坏行丢弃整段历史。
- 全部非法或过滤后为空：抛现有 `IllegalStateException`，由降级链尝试下一数据源。
- 不校验成交量必须大于 0，避免把真实的零成交量误判为价格数据错误。

不在 `MarketDataSourceChain`、落库 handler 或前端重复加同一规则：解析器是所有中证 K 线调用共用的边界，修改面最小。

## 历史数据处理

代码发布后，生产先执行只读统计确认受影响代码/日期，再备份待处理行。清理或回填使用现有行情源和落库流程，完成后验证：

1. 受影响代码不再存在非正 OHLC。
2. 有效日期连续性、最新日期和成交量仍符合预期。
3. K 线接口与页面价格轴不再被 0 拉伸。

生产写库是独立门禁；失败时用备份恢复原记录，代码发布可单独回滚。
