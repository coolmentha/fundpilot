# 实施计划

1. 增加先访问同花顺主页建立 Cookie 会话的 `indexflash` 客户端；补固定 JSON 夹具，解析 `zdt_data` 等长数组的末项。
2. 扩展解析器、`MarketBreadthSnapshot`、Redis 快照和控制器视图；刷新时仅在上涨、下跌、涨停、跌停均有效时发布完整快照。
3. 扩展 `PortfolioOverview`，用现有 `useMarketBreadth` 展示涨停/跌停，不增加请求。
4. 运行后端相关单测、前端 lint/test/build，并验证接口请求链路不直连外部源。
