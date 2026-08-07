# AKShare 基金与指数源研究

研究日期: 2026-08-07

## 结论

不引入 Python/AKShare 运行时依赖。Java 直接调用 AKShare 当前实现所使用的外部接口，并只把与现有盘中估值契约等价的数据接入缓存链。

| AKShare 能力 | 外部接口 | 数据语义 | 覆盖范围 | 本次处理 |
|---|---|---|---|---|
| `fund_value_estimation_em` (AKShare 1.18.12) | `https://api.fund.eastmoney.com/FundGuZhi/GetFundGZList`，`type/pageIndex/pageSize` 参数 | JSON 估算净值和估算涨跌率 | 理论上按基金类型全量 | 当前实测 `Data=null`，不直接接入 |
| 页面兼容备用入口 | `https://fund.eastmoney.com/fundguzhi{page}.html` | 页面行中的 `data-gz` 估算净值和估算涨跌率 | 当前静态页为开放式基金分页，实测第 1-7 页有数据 | 接入，作为同花顺之后的批量估值备用源；2026-08-07 live 第 1 页可解析 |
| `fund_etf_spot_em` | `https://88.push2.eastmoney.com/api/qt/clist/get`，`fs=b:MK0021...` | 场内 ETF 交易行情和 `f441` IOPV 实时估值 | ETF/场内基金 | 接入 ETF 独立估值分支；IOPV 与同花顺最近确认净值配对，交易价不替代净值；本机 live 请求被代理远端提前断开 |
| `fund_etf_hist_em` / `fund_lof_hist_em` | `push2his.eastmoney.com/api/qt/stock/kline/get` | ETF/LOF 交易价格历史 K 线 | ETF/LOF | 不接入盘中估值；交易价格不是基金净值估算 |
| `fund_open_fund_info_em` | `https://fund.eastmoney.com/pingzhongdata/{code}.js` | 开放式基金确认净值历史 | 开放式基金 | 现有东方财富净值降级源已复用该接口，不作为盘中估值 |
| `fund_etf_spot_ths` | `https://fund.10jqka.com.cn/data/Net/info/ETF_rate_desc_0_0_1_9999_0_0_0_jsonp_g.html` | ETF 当前/最近已公布单位净值和日增长率 | ETF | 接入为 IOPV 配对所需的最近确认单位净值；本机 live 返回 HTTP 200、约 1662 条记录；日增长率不冒充盘中估值 |
| `fund_etf_category_sina` / `fund_etf_hist_sina` | 新浪 ETF/LOF 列表与 `realstock/company/{symbol}/hisdata_klc2/klc_kl.js` | 场内交易价格历史 | ETF/LOF | 不接入普通估值链；交易价不是基金净值 |
| `stock_zh_index_daily_tx` | `https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get` | 前复权指数 OHLCV 日线 | sh/sz 交易所指数 | 接入指数 K 线降级链；2026-08-07 live 的 sh/sz 有数据，CSI 主题指数返回空后继续降级 |
| `stock_zh_a_hist_tx` | 同 `stock_zh_index_daily_tx`，按年请求 `symbol,day,start,end,640,{adjust}` | 前复权/不复权股票 OHLCV 日线 | 沪深京 A 股 | 与已接入腾讯日线接口线协议相同；当前无股票历史消费者，不单独复制客户端 |
| `stock_zh_a_tick_tx_js` | `http://stock.gtimg.cn/data/index.php`，`appn/action/c/p` 分页 | 收盘后提供的股票历史分笔成交 | 沪深 A 股 | 无基金估值语义和当前消费者，不接入 |
| `stock_zh_ah_tx` | `stock.gtimg.cn` / `web.ifzq.gtimg.cn` 分页与 K 线接口 | 港股 A+H 实时/历史交易行情 | 港股 A+H | 不属于基金净值或当前指数 K 线边界，不接入 |
| `stock_zh_index_daily` | 新浪指数历史接口 `finance.sina.com.cn/realstock/company/{symbol}/hisdata_klc2/klc_kl.js` | 指数历史 OHLCV | sh/sz 交易所指数 | 暂不接入；响应需要 AKShare 的 JS 解码，现有同花顺/腾讯已覆盖该边界 |
| `fund_open_fund_daily_em` | `https://fund.eastmoney.com/Data/Fund_JJJZ_Data.aspx`，`page=1,50000` | 当日已公布开放式基金单位/累计净值 | 开放式基金 | 属确认净值，不作为盘中估值；现有净值历史链继续按基金拉取完整历史 |
| `fund_etf_fund_daily_em` | `https://fund.eastmoney.com/cnjy_dwjz.html` | 场内基金已公布单位净值和交易价 | ETF/LOF 等场内基金 | 确认净值/交易价混合，不直接写盘中估值链 |
| `fund_lof_spot_em` | `https://88.push2.eastmoney.com/api/qt/clist/get`，`fs=b:MK0404...` | LOF 场内交易价和涨跌幅 | LOF | 交易价不是基金净值估算，不接入普通估值链 |
| 旧 `fundgz` | `https://fundgz.1234567.com.cn/js/{code}.js` | 单基金 JSONP 估值 | 理论上单基金 | 当前实测页面不存在，保留最后兼容回退但不依赖 |

## 已验证字段

静态页的 `#gsdata` 提供估算日期，`#dwjzdata` 提供最近公布净值日期；`#tableContent tr` 中：

- 第 3 个单元格是基金代码；
- 第 5 个单元格 `data-gz` 是估算净值；
- 第 6 个单元格 `data-gz` 是百分比字符串，例如 `0.78%`，转换为现有小数涨跌幅时除以 100；
- 第 10 个单元格是最近公布单位净值，但本次只用其日期标签作为 `baseNavDate`，不把单位净值写入估值快照。

静态页没有分钟级估值时间。本次快照的 `estimateTime` 使用页面估算日期加 Java 读取时的北京时间分钟，仅用于现有“北京时间当天”新鲜度判断和前端展示；这不会伪造分钟曲线。

## 腾讯指数源调用方式

AKShare `stock_zh_index_daily_tx` 先按 `sh000300`、`sz399001` 形式拼接市场和代码，调用：

```text
GET https://proxy.finance.qq.com/ifzqgtimg/appstock/app/newfqkline/get
    ?_var=kline_dayqfq
    &param=sh000300,day,2026-01-01,2026-08-08,640,qfq
    &r=0.8205512681390605
```

响应是 `kline_dayqfq={"data":{"sh000300":{"day":[...]}}}`，每行前六列为日期、开盘、收盘、最高、最低、成交量。Java 不执行远端 JS，只提取变量赋值后的 JSON；`2.*` CSI 指数不请求腾讯，避免已知空数据请求放大。

实测结果：`sh000300`、`sz399001`、`sh000001`、`sh000852` 有日线；`sh930713` 返回空数组。腾讯因此放在中证指数公司之后、同花顺之前，专门补齐交易所指数的可用性，不能替代中证主题指数官方源。

## 请求和降级

请求头沿用东方财富现有 `Referer`/`User-Agent`、1 秒连接超时、3 秒读取超时、共享令牌桶和不重试策略。页面分页方式参考 AKShare 的基金估值页面入口，顺序请求页面，遇到空页或 404 停止；结果在 Java 进程内缓存 1 分钟，多个基金查询复用同一批结果。

估值顺序为：同花顺分钟估值 -> AKShare 静态页批量估值 -> ETF IOPV 与最近确认净值配对(仅交易型 ETF) -> 旧 fundgz 兼容回退。静态页或 ETF 分支超时/解析失败只进入下一源，最终仍由 `MarketRealtimeCache` 负责删除旧估值、设置状态和 5 分钟失败退避。指数 K 线顺序为：中证指数公司 -> 腾讯 -> 同花顺 -> 东方财富；腾讯只处理 `1.*`/`0.*` 交易所指数，CSI `2.*` 由中证源失败后直接进入同花顺。
