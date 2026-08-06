# 线上只读证据

来源：本任务开始前对 `fundpilot-prod` 的只读检查及页面复现记录。

- `931865.CSI` 共 1233 条 `index_kline` 记录，其中 529 条 `open/high/low=0`。
- 该异常区间为 `2021-07-05` 至 `2023-09-04`；有效 OHLC 从 `2023-09-05` 开始。
- 异常记录的成交量字段仍为非零，因此不是成交量丢失；零价格把前端价格轴拉到 0，造成截图中的巨大空白。
- `H30590.CSI` 另有 59 条类似记录，区间为 `2021-07-06` 至 `2021-09-28`。
- 线上 7 只持仓基金的 K 线接口均能返回，成交量柱存在，前端无控制台错误；问题集中在历史非法价格记录。

代码证据：`CsindexJsParser` 原先直接读取 `open/close/high/low`，`MarketDataSourceChain` 只判断结果非空，`MarketIndicatorRefreshCommandHandler` 随后直接 upsert 到 `index_kline`。
