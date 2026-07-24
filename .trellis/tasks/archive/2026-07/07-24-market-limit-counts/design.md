# 大盘涨停跌停家数设计

## 边界

扩展既有 `MarketBreadthSnapshot` 与 `GET /api/market/breadth` 的只读投影；不增加浏览器直连，也不改变基金估值、指数或板块刷新链路。

## 数据流

后台行情刷新从已验证的东方财富涨停池、跌停池响应解析同一交易日的总数，与现有 `f104/f105` 上涨/下跌家数合成完整快照，写入内存和 Redis。前端沿用 `useMarketBreadth` 轮询已有接口。

## 一致性与降级

只有四项都有效且池日期为北京时间当日时才替换缓存；池空、字段缺失、日期不匹配、请求或解析失败均保留上次完整快照。无历史完整快照时 API 返回 `null`，前端显示现有不可用状态。

## 未决验证

候选 `push2ex.eastmoney.com/getTopicZTPool` / `getTopicDTPool` 在当前环境返回 `rc=102,data=null`。同花顺 `https://q.10jqka.com.cn/api.php?t=indexflash` 在携带浏览器会话 Cookie 时返回 JSON；`zdt_data.zd_time`、`ztzs`、`dtzs` 等长，末项为当前涨停/跌停数。新 `ThsIndexFlashClient` 使用标准库 Cookie 会话，先请求 `https://q.10jqka.com.cn/` 再请求 `indexflash`；仓库不保存实际 Cookie。任一请求失败时保留上一完整缓存。
