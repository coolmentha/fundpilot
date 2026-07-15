# 同花顺公开行情接口验证

验证日期：2026-07-15。以下接口均无需登录，使用浏览器 User-Agent 与同花顺 Referer 实测返回 200。

## 基金净值

单位净值：

```
GET https://fund.10jqka.com.cn/{fundCode}/json/jsondwjz.json
```

响应：`var dwjz_{code}=[[yyyyMMdd,value],...];`

累计净值：

```
GET https://fund.10jqka.com.cn/{fundCode}/json/jsonljjz.json
```

响应：`var ljjz_{code}=[[yyyyMMdd,value],...];`

代表性对照：

| code | 类型 | 同花顺单位/累计最新日期 | 结论 |
|---|---|---|---|
| 510300 | ETF | 2026-07-15 | 两数组各 3456 条，字段语义与东方财富一致 |
| 000082 | 股票 | 2026-07-14 | 两数组各 3173 条 |
| 000041 | QDII | 2026-07-14 | 两数组各 4370 条，体现滞后日期 |
| 000009 | 货币 | 2026-07-15 | 接口有数据，但业务口径不是本项目支持的普通基金净值，必须在 orchestration 层拦截 |
| 180101 | REIT | 2025-12-31 | 接口有数据，但本任务明确不支持 REIT 收益模型，必须拦截 |

同花顺 510300 最新单位净值 `4.8336`、累计净值 `2.1195`，与东方财富对应字段一致。同花顺需要同时请求两条接口，再按日期关联。

## 基金字典

同花顺数据中心页面使用 `JsonpDataLoader`，实际地址模板为：

```
https://fund.10jqka.com.cn/data/Net/info/
  {type}_{key}_{sort}_{start}_{end}_{page}_{count}_{orgid}_{code}_{compare}_jsonp_g.html
```

全量验证地址：

```
https://fund.10jqka.com.cn/data/Net/info/all_code_asc_0_0_1_40000_0_0_0_jsonp_g.html
```

响应约 7 MB：`g({"data":{"info":{"count":23150,...},"data":{"f000001":{...}}}})`。
条目包含 `code`、`name`、`typename`，可映射为现有 `FundDictEntry(code,name,rawName)`。该接口只在东方财富字典失败时调用，避免正常路径下载两份全量字典。

原页面还暴露 `fund.ijijin.cn` 地址，但其 HTTPS 证书主机名校验失败；同一路径在
`fund.10jqka.com.cn` 可通过标准 Java TLS 校验并返回相同数据，因此生产默认使用同花顺主域名。

## 指数 K 线

最近 140 根日线：

```
GET https://d.10jqka.com.cn/v6/line/{internalCode}/01/last.js
```

响应：`callback({"name":"...","data":"date,open,high,low,close,volume,...;..."})`。

已验证映射：

- 上证指数 `000001.SH` -> `hs_1A0001`
- 其他 `000xxx.SH` -> `hs_1B` + code 后四位，例如 `000300.SH -> hs_1B0300`
- 深证 `399xxx.SZ` -> `hs_399xxx`
- CSI/主题指数 -> `120_` + 原代码，例如 `930713.CSI -> 120_930713`、`H30590.CSI -> 120_H30590`

`last.js` 对主流指数返回 140 根，足够现有 VolumeState 与图表降级展示。个别主题指数返回 1 根或 total=0，应按 empty 继续/失败处理，不伪造完整历史。年文件 `/{year}.js` 可返回完整年度日线，但一次降级需要多次请求，会破坏 15 秒在线预算，本次不采用。

## Implementation Consequences

- 使用三个 raw client：fund NAV、fund info dictionary、index line；由一个 `ThsMarketDataSource` 聚合为现有 `MarketDataSource`。
- 普通净值回退需要两个 HTTP 请求；外呼超时应收紧到 connect 1 秒/read 3 秒，并限制东方财富令牌等待，保证最坏链路仍小于前端 15 秒。
- 货币基金/REIT 的不支持判断必须发生在持有 FundEntity/FundDict 类型信息的 service 层，不能仅凭同花顺数组形态判断。
- 所有 JSONP/变量赋值都使用受限包裹提取 + Jackson，不执行远端 JavaScript。
