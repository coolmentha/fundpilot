# 技术设计

## 目标

参考 AKShare 基金估值页面入口的真实分页请求和 HTML 解析方式接入现有基金估值降级链；本机 AKShare 1.18.12 的 `fund_value_estimation_em` 旧 JSON API 当前返回 `Data=null`，不直接复刻，修复旧东方财富单基金备用接口失效导致的估值失败放大。

## 边界

- Java 直接调用东方财富静态页，不新增 Python/AKShare 依赖；静态页对应 `fundguzhi{page}.html`，是兼容备用入口，不宣称等同于 AKShare 1.18.12 当前函数实现。
- 静态页只接入估算涨跌率，ETF 分支只接入 IOPV 与最近确认净值计算出的估算涨跌率；不改变 `FundEstimateSnapshot`、`FundEstimateResult`、`EstimateStatus` 或 Redis JSON 结构。
- ETF IOPV 仅对交易型 ETF 接入独立估值分支；它必须与同花顺最近确认单位净值配对后才进入普通估值结果。ETF/LOF 交易价、历史净值及腾讯股票历史/分笔/A+H 仍没有当前业务消费者，不接入估值链。
- 不改 `MarketRealtimeCache` 的旧值删除、北京时间当天判断、状态枚举或 5 分钟失败退避。

## 数据流

```text
同花顺分钟估值
        |
        v 失败/空
AKShare 静态估值页批量缓存(1 分钟, 1..99 页遇空/404停止)
        |
        v 未命中/失败
ETF IOPV + 最近确认单位净值(仅交易型 ETF)
        |
        v 未命中/失败
旧 fundgz 单基金兼容回退
        |
        v
FundEstimateResult -> MarketRealtimeCache -> Redis/前端三态

指数 K 线独立使用：中证指数公司 -> 腾讯 `stock_zh_index_daily_tx` -> 同花顺 -> 东方财富。
腾讯只覆盖 `1.*`/`0.*` 交易所指数，`2.*` CSI 指数跳过腾讯；不把 ETF/LOF 交易价放进基金估值链。
```

`FundEstimateService` 保留逐基金对外方法，以兼容现有调用方；新增源只在服务内部按页批量加载并按基金代码查表。这样同一进程内多个基金不会重复请求同一组静态页。

## 解析契约

新增 Jsoup 解析器读取 `#gsdata`、`#dwjzdata` 和 `#tableContent tr`。估算增长率 `data-gz` 去掉 `%` 后除以 100；缺代码、日期、估算值或数字格式错误的行跳过。页面没有有效表格时返回空结果，结构损坏由服务标记解析失败并继续回退。

静态页只有日期，服务用该日期加当前北京时间 `HH:mm` 组成 `estimateTime`。`MarketRealtimeCache` 仍只依据日期判定当天新鲜度。

## 失败和可观测性

- 页面请求使用现有东方财富限流器、请求头、超时和 `Retryer.NEVER_RETRY`。
- 1..99 页顺序请求；空结果或 404 代表分页结束，不继续放大请求。
- 批量加载成功、空结果、超时、解析失败和普通失败写入 `MarketDataMetrics`，源名为 `EastmoneyFundEstimatePageClient`。
- 批量缓存失败在短 TTL 内复用失败结果，避免每只基金再次请求同一批页面。
- 最终单基金状态仍由已有 `MarketRealtimeCache` 决定，失败不复用旧估值。

## 回滚

删除新静态页客户端、ETF IOPV 客户端/服务和 `FundEstimateService` 的新回退分支即可回到原有同花顺 -> fundgz 路径；不涉及数据库、Redis schema 或外部配置必填项。
