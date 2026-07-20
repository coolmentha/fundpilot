# 指数 K 线数据源:中证指数公司(csindex.com.cn)替代被限流的 push2his

## 背景

行情工作台 K 线图(ADR:K 线图 klinecharts v9)对基金 1(跟踪 930713.CSI 中证人工智能主题指数)持续报错:

```
fetchIndexKlineWithPeriod code=2.930713 失败数=2
数据源[EastmoneyMarketDataSource] fetchIndexKline 失败 code=2.930713:
  Unexpected end of file from server executing GET https://push2his.eastmoney.com/...
```

根因:东方财富 `push2his.eastmoney.com` 对 VPS 出口 IP 在重复请求后 IP-blocks(http_code=000,连接被对端直接关闭,非瞬时抖动)。
v0.4.5 已加 `index_kline` 本地缓存 + `KlineService` 读缓存,但缓存靠 `MarketDataFetchService` 每日 refresh 时拉 push2his 顺便落库——
push2his 被封则缓存永远空,陷入「封了→拉不到→缓存空→图表降级净值」死循环。

调研 akshare(`stock_zh_index_hist_csindex`)与 capitalfarmer 后确认:CSI 主题指数(930xxx)的官方发布方中证指数公司
提供公开接口 `www.csindex.com.cn/csindex-home/perf/index-perf`,返回 OHLCV 日线 JSON,不封 IP、不要求 Referer/Cookie。

## 决策

新增 `CsindexMarketDataSource`(实现 `MarketDataSource`),置于 `MarketDataSourceChain` 链首 `[csindex, eastmoney, ths]`。
指数日 K 主源改走 csindex,绕开被限流的 push2his。

- **覆盖范围**:CSI 主题指数(930713 等)+ 中证编制沪市指数(000300 沪深300、000016 上证50、000852 中证1000)由 csindex 命中。
  深交所指数(399xxx)csindex 返空 `data[]` → `CsindexJsParser` 抛 `IllegalStateException` → 链回退 eastmoney。
- **secid 处理**:链路传 secid("2.930713"/"1.000300"),csindex 要裸代码,`CsindexMarketDataSource.bareCode` 剥 "X." 前缀。
- **周期**:csindex 仅日 K。`fetchIndexKlineWithPeriod` 先拉日 K,再用 `CsindexJsParser.aggregate` 在源内聚合周/月 K
  (open=首日、high=max、low=min、close=末日、volume=sum,date=周期末日)——语义同 `KlineService` 缓存路径聚合。
- **不支持操作**:`fetchNavHistory`/`fetchFundDict` 抛 `UnsupportedOperationException`(csindex 只发指数)。
  `MarketDataSourceChain.tryEach` 对该异常**静默跳过**(不记 warn),直接回退 eastmoney,避免链首专用源污染日志。

## Considered Options

- **A. 继续用 push2his + 重试/代理〔否决〕**:v0.4.4 加重试无效——是持久 IP 封锁非瞬时抖动;换出口 IP(住宅代理)增基础设施复杂度。
- **B. 腾讯/新浪替代〔否决〕**:实测 `web.ifzq.gtimg.cn` 与新浪 `money.finance.sina.com.cn` 仅承载 sh/sz 交易所指数,
  对 930713.CSI 返空/null(CSI 主题指数非交易所挂牌,腾讯/新浪不收录)。
- **C. 中证指数公司官方接口〔已采纳〕**:发布方自有数据,口径权威,不封 IP。借鉴 akshare `stock_zh_index_hist_csindex`。
- **D. eastmoney searchapi 解析 secid〔未采纳〕**:secid 已由 `SecidFormat` 正确生成(`.CSI`→`2.`),非 secid 错误,无需引入搜索解析。

## Consequences

- **正面**:930713.CSI 等 CSI 主题指数 K 线恢复,缓存可填充(`MarketDataFetchService` refresh → csindex → `upsertIndexKline` → `index_kline`),
  打破死循环;沪深300/上证50/中证1000 等主流基准也改走 csindex,降低对 push2his 的依赖。
- **负面/边界**:csindex 仅日 K(周/月靠源内聚合,非原生);仅中证编制指数，`0.*` 深交所指数直接跳过本源并回退 eastmoney；
  `tradingVol` 单位为股(push2his 为手),图表成交量只需序列内一致,跨源不混用故无碍。
- **时序**:`MarketDataFetchService.fetchOne` → `marketDataSource.fetchIndexKline`(链首 csindex)→ `upsertIndexKline` 落库 →
  `KlineService.getKline` 读缓存聚合日/周/月。缓存空时 `KlineService` 实时拉(链首 csindex)兜底。

## 验证

- 单测:`CsindexJsParserTest`(6)+ `CsindexMarketDataSourceTest`(6)+ `MarketDataSourceChainTest`(4,回归)全绿。
- live smoke:`CsindexClientLiveSmokeTest`(3)真实 csindex HTTP——930713/000300 返数据,399006 抛异常让链回退。
- live 流程:`CsindexCacheFlowLiveTest`(3)真实 Spring 链 bean 拉取 930713 日 K(>200 根)+ 周 K 聚合 + `index_kline` 落库 roundTrip。
- 全量回归:97 项 market 包测试全绿(含 KlineServiceTest / MarketDataFetchServiceTest)。
