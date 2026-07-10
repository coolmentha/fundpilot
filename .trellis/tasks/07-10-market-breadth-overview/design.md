# 技术设计

## Data Flow

```text
东方财富 ulist.np/get
  -> EastmoneyJsParser
  -> MarketRealtimeCache
  -> GET /api/market/breadth
  -> useMarketBreadth
  -> PortfolioOverview
```

## Backend Design

- 在 `EastmoneyPush2Client.fetchIndexRealtimeRaw` 的字段列表中增加 `f104`、`f105`，保持单次批量请求。
- `MarketRealtimeCache` 将用户自选指数与固定市场宽度 secid 去重合并后请求一次：
  - `1.000001` 上证指数
  - `0.399001` 深证成指
  - `0.899050` 北证 50
- 原有 `indexCache` 仍只按用户自选列表返回，固定市场宽度标的不泄漏到自选指数接口。
- 新增 `MarketBreadthSnapshot`，由解析器校验三个市场均存在且 `f104`、`f105` 完整后求和。该值表示当日有涨跌状态的沪深京股票，不宣称等于全部上市 A 股数量。
- 新增独立 `breadthCache`。解析结果无效时保留旧值，首次无值时为 `null`。
- 新增 `MarketBreadthView` 和 `GET /api/market/breadth`，Controller 只做缓存到 View 的映射。

## Frontend Design

- 新增 `useMarketBreadth`，按现有实时行情模式轮询后端缓存。
- `PortfolioOverview` 保持组合摘要为主查询；市场宽度查询失败时仅市场宽度卡显示 `-`，不把整个总览切换为错误态。
- 总览由 3 张卡扩展为 4 张卡：全仓收益、上涨基金、下跌基金、大盘涨跌进度条。
- 进度条使用两个相邻色块：左红表示上涨，右绿表示下跌；宽度分别由上涨、下跌占两者合计的比例决定。家数与百分比放在进度条外的固定标签行，避免极端比例时文字溢出。
- 涨跌合计为 0 或数据不可用时显示空轨道；使用 `aria` 文本暴露上涨、下跌家数和比例。
- 桌面使用适合四项总览的网格；中窄屏逐级降为两列和单列。

## Failure And Compatibility

- 不改变现有 `/api/market/indices/realtime` 响应结构。
- 不新增数据库表、迁移或依赖。
- 外部接口失败沿用“保留旧缓存”策略。
- 固定市场宽度 secid 只在后端定义，前端不感知东方财富代码。

## Validation

- 解析器测试覆盖正常汇总、缺市场、缺字段和空响应。
- 缓存测试覆盖无自选指数仍刷新宽度、单请求复用、无效响应保留旧缓存。
- 运行后端目标测试与完整测试。
- 运行前端 `npm run build`，检查响应式样式无编译问题。
