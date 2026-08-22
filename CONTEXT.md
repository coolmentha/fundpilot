# FundPilot · 基金纪律策略上下文

本上下文描述 FundPilot 后端"基金纪律策略"领域的核心术语与边界。它是领域语言的词典，不是规格说明书。实现细节、字段定义、状态机见
`backend/docs/` 下的设计文档。

## 基金、组合与核算

**基金产品（FundProduct）**:
市场中可被多个用户共同识别的基金产品，身份由基金代码确定，并承载名称、产品类型和跟踪指数等公共属性。基金产品不表达任何用户是否关注、持有或作废该基金。
_Avoid_: 用 Fund 同时表示市场产品和某个用户的基金记录

**组合基金（PortfolioFund）**:
某个用户将一只基金产品纳入个人组合后形成的记录，承载分组、关注状态、仓位提醒和生命周期。不同用户引用同一基金产品时，拥有彼此独立的组合基金。
_Avoid_: 把用户级分类或生命周期写回基金产品

**持仓（Position）**:
用户在某只组合基金上的当前事实头寸，由已确认交易、份额批次和成本共同确定。持仓归核算领域所有；组合领域只消费持仓结果，不自行维护第二套份额或成本事实。
_Avoid_: 把 Position 当作 PortfolioFund 的可直接修改字段

**基金产品类型（FundProduct Type）**:
描述基金产品客观形态及投资标的的分类，包括交易/管理形态和投资目标。它决定行情能力与适用的数据来源，不表达用户的纪律偏好。
_Avoid_: 用基金产品类型表示止盈模板或用户风险偏好

**产品费率表（Fund Fee Schedule）**:
基金合同或销售渠道公布的申购费率、优惠费率、销售服务费率和赎回费率阶梯，是按基金产品代码识别的公共事实。它不表示某笔交易最终产生的手续费金额。
_Avoid_: 把产品费率表归入用户持仓；把交易实际手续费写回产品费率表

**纪律分类（Discipline Category）**:
用户为组合基金选择的纪律参数分类，用于推荐止盈参数。它可以由产品信息推断并由用户覆盖，不改变基金产品本身的客观类型。
_Avoid_: 将纪律分类与基金产品类型合并

**组合基金作废（Void PortfolioFund）**:
用户确认组合基金因代码或身份录入错误而无效。作废是不可恢复的终态；记录与审计证据保留，但该组合基金及其交易、收益、纪律和计划完全不参与任何业务计算。
_Avoid_: 用作废表示卖出清仓；作废后恢复；仅从列表隐藏但继续核算

**清仓（Clear Position）**:
有效交易使持仓份额归零。清仓不是删除或作废：组合基金退出当前持仓列表并进入已清仓历史，历史交易及已实现收益继续计入组合累计收益；后续再次买入可形成新的持仓周期。
_Avoid_: 清仓时删除交易；把已清仓基金完全排除累计收益；把清仓当作不可恢复终态

**建议回应（Recommendation Response）**:
用户对一条纪律建议作出的采纳或忽略决定。采纳只表达用户意图并产生待确认交易；在交易确认前，不改变事实持仓。
_Avoid_: 把建议回应称为成交；回应时直接修改持仓

## 信号与交易动作

**SignalType（信号类型）**:
系统在交易日 14:50 第三批行情快照完成后，对绑定 `EFFECTIVE` 策略的基金评估并固化进 `SignalLogEntity`，站在**用户视角的策略动作**。枚举为四值：
`NONE`（无建议）/ `BUILD`（建仓）/ `ADD`（加仓）/ `SELL`（卖出）；当前引擎只生成 `NONE/SELL`，`BUILD/ADD` 仅为存量日志兼容保留。
_Avoid_: DECREASE（DECREASE 是账目方向，不是策略意图）

**FundTransactionSource（交易来源）**:
账目层份额变化方向的中性描述，七值：`INCREASE` / `DECREASE` / `TRANSFER_IN` / `TRANSFER_OUT` / `INVEST` /
`ADJUST_IN` / `ADJUST_OUT`。与 `SignalType` 是**两个不同维度**——信号描述"系统建议什么策略动作"，来源描述"账目份额怎么动"。
`ADJUST_IN/ADJUST_OUT` 仅用于账实份额修正，录入即确认，不计算净值、金额或手续费。
_Avoid_: BUY / SELL（语义已被 SignalType 占用，避免歧义）

**信号回应（Signal Response）**:
路径 `fundId` 必须与 `signalLogId` 所属基金一致；同一 SignalLog 最多生成一笔未软删交易。回应只创建 PENDING 交易，
不提前修改 `FundStatus`；交易确认或撤销后再按全部 CONFIRMED 交易的事实净份额统一重算状态。SELL 交易同样保留 `signalLogId`。
_Avoid_: 仅相信请求路径基金；创建 PENDING 时提前切到 HOLDING/CLEARED；SELL 丢失 SignalLog 关联

## 回撤基准

**前高（peakNav）**:
基金历史最高累计净值，逻辑止损回撤判定的参考。**不存字段，实时派生**——`max(fund_nav_history.accumulated_nav)`，配
`(fund_id, nav_date)` 索引毫秒级返回。净值修正、补录历史、job 异常都不会导致失真。
_Avoid_: 在 `FundEntity` 上存 `peakNav` 字段（派生值落库会失真，见 ADR-0001）

**持有期高点（holdingPeriodPeakNav）**:
建仓后该基金出现的最高累计净值，是持仓期行情分析指标。**不存字段，实时派生**——
`max(fund_nav_history.accumulated_nav) WHERE nav_date >= fund.openedAt`。`FundStatus = HOLDING` 时才有意义；
`CLEARED → PENDING_HOLDING` 时因字段不存在，自然无需清理。
_Avoid_: 在 `FundEntity` 上存 `holdingPeriodPeakNav` 字段（同前高，派生值落库会失真）

## 卖出纪律

**定投止盈（DCA Take-Profit）**:
以整仓成本为盈利基准的周期性浮盈收割机制。整体收益率达到 `profitActivationPercent` 后进入 `ARMED`，记录本周期高点；
从周期高点回撤达到 `stopLossPullbackPercent` 后生成一次 `TRAILING_STOP` 卖出建议。同一周期只触发一次，交易确认后进入冷静期；
冷静期结束且收益仍达标时，以当天净值建立新周期高点，当天不卖，等待下一次新回撤。
_Avoid_: 未盈利就把普通下跌称为止盈；同一回撤区间每日重复卖出；每次定投重置整仓止盈保护

**基金类型推荐参数（Take-Profit Preset）**:
推荐依据只用 `FundCategory`，不使用决定数据源路径的 `FundSubType`。宽基推荐 15% 启动/6% 回撤，行业 20%/8%，主动 15%/7%，混合 12%/5%；
系统只在新建或用户主动“恢复推荐值”时应用模板。用户可修改所有参数，`customized=true` 后基金类型或模板版本变化不得静默覆盖。
所有比例按正数存储，例如 `0.06` 表示回撤 6%。
_Avoid_: 用负数表达回撤；把推荐值当强制值；基金类型变化后自动覆盖生效策略

**定投止盈建议份额（Take-Profit Suggested Shares）**:
建议份额取四项最小值：`浮盈×收割比例÷当前净值`、`当前份额×单次上限`、成熟可赎回份额、`当前份额×(1-最低保留仓位)`。
成熟份额按 `fund_lot` 逐笔计算 5 个交易日保护；新定投 lot 只保护自身，旧 lot 仍可止盈。实际卖出继续按 FIFO 和真实赎回费率确认。
_Avoid_: 最近一次定投锁住全部历史持仓；止盈突破最低保留仓位；绕过 FIFO lot 直接假设所有份额都可低费赎回

**逻辑破坏止损（Logic-Broken Stop-Loss）**:
趋势死亡型止损，和移动止盈完全不同——不分档、一次清空全部持仓。触发后 `FundStatus → CLEARED`，**突破 7 天内不赎回硬约束**
（豁免 MIN_HOLD_DAYS）。两类基金判定条件不同（按 `fundSubType` 分派）。
_Avoid_: 用基本面突变（基金经理变更等）判定——本期无公告数据源，等下一期接公告源再做

**逻辑止损 · ETF/指数/指数增强基金判定**:
三个条件**同时**命中才触发：① 净值跌破年线（最近累计净值 < 250 日累计净值均线）② 周 MACD 绿柱扩大 ③ **跟踪指数**
放量下跌（当日成交量 > 20 日均量 × 1.5 且当日收盘跌）。跟踪指数取 `FundEntity.benchmarkIndexCode`。
_Avoid_: 用基金自身净值算量能——基金没有成交量

**逻辑止损 · 主动/混合基金判定**:
两个条件**同时**命中才触发：① 净值跌破年线 ② 周 MACD 绿柱扩大。主动基金无跟踪指数、无真实成交量，
原第三条件"单周跌幅 > weeklyCoolDownThreshold"随金字塔加仓移除（`weeklyCoolDownThreshold` 字段已删，V10 迁移）——
破年线+MACD绿柱扩大已足够表达趋势死亡。
_Avoid_: 用沪深300 量能代理（反映大盘情绪不反映个股层面）或持仓股聚合量能（数据滞后一季度，实战价值打折）

**SELL 信号优先级**:
一只基金每日一行 SignalLog，日期按北京时间自然日映射为 UTC 00:00 标签。SELL 信号最多一类。`evaluateSignal` 按"**逻辑止损 > 移动止盈**"顺序检查，命中即返回。
`reason` 两值：`LOGIC_BROKEN` / `TRAILING_STOP`（`REBALANCE` 已废弃，存量数据可见）。
_Avoid_: 同日多类型 SELL 信号叠加（违反"一只基金每日一行"的唯一性约束）

**7 天内不赎回硬约束（MIN_HOLD_DAYS）**:
保护性约束，防止刚买入份额立即赎回。定投止盈按 `fund_lot.acquireDate` 对每笔剩余份额计算 **5 个交易日**，仅未成熟 lot 不参与建议份额；
逻辑止损仍可一次清空，并在最近买入未满窗口时记录 `MIN_HOLD_DAYS_OVERRIDDEN`。手动卖出不经过止盈建议计算，实际赎回费仍由 FIFO lot 阶梯决定。
_Avoid_: 用最近一次买入时间锁住整仓；用自然日近似交易日；逻辑止损受最低保留仓位限制

**交易日历（TradingCalendar）**:
`MIN_HOLD_DAYS` 判定 5 个交易日所需的基础数据表，记录每个日期是否为 A 股交易日（含节假日剔除）。一次性灌入未来几年的日历即可（A
股节假日规则相对固定）。当前由新浪交易日历源在启动时预热并每日同步：空表全量初始化，非空表只写当前最大日期之后的数据；
管理入口保留全量补写历史缺口。数据库使用原子 insert-if-absent 保证重复和并发同步幂等。

## 策略状态机

**策略状态机（StrategyParamStatus）**:
金字塔退场 + 回测/寻优移除后，状态机简化为：`PENDING_CALIBRATION` --activate--> `EFFECTIVE` --retire--> `PENDING_CALIBRATION`。
不再有 `calibrate` 动作和 `CALIBRATED`/`CALIBRATION_FAILED` 流转——回测本身是金字塔寻优配套，金字塔没了回测无意义，
移动止盈阈值无需回测验证。`CALIBRATED`/`CALIBRATION_FAILED` 枚举值保留供存量数据兼容。同基金同时最多一份 `EFFECTIVE`
（数据库 `uq_fund_strategy_effective` 兜底）。`CLEARED → PENDING_HOLDING` 时全员回退 `PENDING_CALIBRATION`。
`FundStrategyEntity` 同时保存定投止盈配置版本与该版本运行时周期：推荐参数、`customized`、`TakeProfitPhase`、周期高点和冷静期时间。

## 开发顺序

**数据源先行 / 策略主线后做**:
本期工程顺序严格串行：先把东方财富数据源完整做完（净值 + 字典 + 指数 K 线 + 自动识别 + 限流缓存 +
表级缓存），再开始策略主线（信号引擎、状态机、Service、Controller）。理由：策略主线开发时直接有真实数据可用，不用 mock；集成时无
DTO 不匹配返工；上线时没有"半手动灌入"的临时态要拆。代价是前期数小时看不到策略效果。
_Avoid_: 并行推进（B 选项，集成返工风险）；分层交付（C 选项，量能和逻辑止损被切到阶段二）

## 行情数据源

**行情源（MarketData Source）**:
同花顺为净值/字典主源，东方财富/天天基金为真实降级源（issue #186 对齐实现：链序 `[csindex, ths, eastmoney]`，同花顺优先取净值是刻意决定，见提交 427f6bf）；指数 K 线优先中证指数公司。本期接入真实实现，不走半自动灌入。三条数据线：基金净值历史（同花顺 `dwjz_`/`ljjz_` 两次请求，东财 `pingzhongdata.js` 的
`Data_netWorthTrend` / `Data_ACWorthTrend` 单请求兜底）、基金字典（东财 `fundcode_search.js` 全量约 2 万条兜底）、指数 K 线（
`push2his.eastmoney.com`，用于量能指标）。东方财富变量赋值响应只做受限结构提取后交 Jackson，不执行远端 JS；全部手工 Feign client 使用 1s 连接、3s 读取超时，不自动重试；东方财富限流最多等待 1s。
_Avoid_: "本期半自动灌入"的旧定位（已升级为真实接入）

**基金子类型（fundSubType）**:
数据源维度的基金分类，区别于策略参数维度的 `FundCategory`（宽基/行业/主动/混合）。四值：`ETF`（场内交易，可直接拿自身 K 线）/
`INDEX`（指数基金，看跟踪指数）/ `INDEX_ENHANCED`（指数增强，看跟踪指数）/ `ACTIVE`（主动管理，无跟踪指数）。自动识别只走名称启发式（方法
A），未命中兜底为 ACTIVE（方法 C）；**不做持仓股票与指数成分股重合度反推**（方法 B，本期跳过，留给将来）。
_Avoid_: 把 `fundSubType` 和 `FundCategory` 合并（用途不同：前者决定数据源和逻辑止损判定路径，后者决定定投止盈推荐参数）

**跟踪/基准指数代码（benchmarkIndexCode）**:
`FundEntity` 上的可空字段。指数/ETF/指数增强基金填实际跟踪指数（如 `000300.SH`）；主动/混合基金不要求跟踪指数，逻辑止损只使用年线与周 MACD。识别流程：名称关键词命中 → 命中失败兜底为 ACTIVE（方法 A + C）。指数类空值降级时逻辑止损不出信号（
`signalType=NONE, reason=INSUFFICIENT_MARKET_DATA`）。漏网指数基金由用户手动补 `benchmarkIndexCode`。
_Avoid_: 主动基金强制要求填跟踪指数（主动基金本质上没有跟踪标的）；本期做持仓股票重合度反推（方法 B，复杂度高，留给将来）

**基金类型自动识别（fundCategory 自动回填）**:
`fundCategory`（宽基/行业/主动/混合）与 `fundSubType` 一样按基金名称启发式识别，**尽力填 + 可覆盖**——识别不准时填最可能值（无关键词的指数类
默认宽基、无关键词的主动类默认主动），用户可在编辑时手动改，不留痕（沿用"override 不留痕"已确认条款）。不阻塞建仓流程，符合"所有信号都是提示"精神。
识别规则：指数类基金（ETF/INDEX/INDEX_ENHANCED）名称含宽基指数词（沪深300/中证500/创业板/上证50/科创50/中证1000）→ 宽基；含行业词
（半导体/医药/新能源/消费/军工/银行等）→ 行业；两者都没命中 → 宽基。主动类基金（ACTIVE）名称含"混合/灵活配置/平衡" → 混合；否则 → 主动。
`rawName`（东方财富"稳健成长型"等风格描述）**不参与 fundCategory 判定**——它描述投资风格不描述资产类别，无法可靠区分宽基 vs 行业。
_Avoid_: 把识别不准的字段留 null（fundCategory 为 null 时无法选择止盈推荐参数，fundSubType 为 null 时策略分支不明确）；用 rawName 判 fundCategory

**基金字典搜索（FundDict Search）**:
新建基金时用户只需输代码或名称二选一，搜索框自动补全候选列表（多候选时让用户选，A/C 份额作为不同条目并列出），选中一条后 code/name/
fundSubType/fundCategory/benchmarkIndexCode 一次性回填。字典落 `fund_dict` 表当缓存（不引入 Redis），首次查询或定时任务拉全量
`fundcode_search.js` 落库，搜索框查本地表——毫秒级响应、不撞东方财富限流、支持模糊检索。落库时同步跑 `fundSubType` + `fundCategory`
识别并缓存识别结果，搜索返回的候选自带分类，避免运行时重复识别。
_Avoid_: 搜索框每次按键现拉东方财富字典（撞限流）；进程内缓存（多实例不一致、重启丢失）

## 行情数据缓存

**表级缓存（MarketIndicatorSnapshot）**:
不引入 Redis，用 PostgreSQL 的 `market_indicator_snapshot` 表当缓存。所有未软删基金按 14:30 / 14:40 / 14:50 三批拉取落库，
之后所有信号生成、用户查看建议都从这张表读，不再发外部请求。14:50 第三批返回后才生成当日信号，不能由两个同秒 cron 竞争顺序。
实时行情缓存使用 Redis AOF 持久化，应用进程保留内存读副本；刷新成功后写穿 Redis，发布重启时先恢复快照。
盘中基金估值按同花顺分钟估值 → 东方财富 fundgz 单点降级；同花顺同次响应的分钟点可供基金详情“今日分时”使用。仅北京时间当日且至少两个有效点的曲线进入缓存；失败、过期或后备单点只显示空态。指数 K 线同轮按唯一 benchmarkIndexCode 拉取复用，深交所 `0.*` 跳过中证源。
_Avoid_: 只使用进程内缓存（发布重启丢失、首次预热失败时页面空白）

**市场宽度（Market Breadth）**:
行情工作台总览中的沪深京股票上涨/下跌家数。数据来自东方财富指数实时接口的 `f104/f105`，固定汇总上证指数
`1.000001`、深证成指 `0.399001`、北证 50 `0.899050` 三个市场口径，与用户自选指数列表解耦。缓存刷新把固定市场
与自选指数合并为一次批量请求，再分别投影到指数缓存和市场宽度缓存，不增加外部请求次数。任一市场缺失时保留旧缓存，
不得展示部分市场合计。前端左红表示上涨、右绿表示下跌，比例只按上涨+下跌计算；字段表示当日有涨跌状态的股票，
不宣称等于全部上市 A 股数量。
_Avoid_: 汇总用户自选指数（成分重叠且口径随配置变化）；任一市场缺失时仍发布部分合计；把平盘加入红绿两段比例

**市场量价状态（Market Volume-Price State）**:
以上证指数最近行情的涨跌幅与量比表达市场价量关系，状态只描述上涨、下跌、平盘及放量、缩量、平稳的组合，不是具体基金交易信号。
_Avoid_: 把市场量价状态当成买卖指令；将指数量比与基金成交量混用

**盘中估值（Intraday Estimate）**:
普通非 QDII 基金在当日确认净值公布前使用的短时涨跌估算。QDII 不使用盘中估值，只按最新确认净值的发现日结算收益；盘中估值不能替代确认净值。
_Avoid_: QDII 盘中估值；把估算净值当作已确认净值；用旧估值冒充当日数据

**当晚净值确认（Daily Nav Confirm）**:
让三态今日涨跌「盘后实际值」生效的机制。每晚 20:00-22:59 每 5 分钟轮询普通基金，事务外拉取净值历史，筛选 `local latest < remote navDate <= target date`，再在短事务内重新校验并幂等落库。不得依赖 fundgz.jzrq，也不要求新净值日期等于今天，因此 FOF/QDII 的滞后公布日期可以按真实 navDate 入库。若晚间仍未公布或请求失败，次日 00:00-09:59 每 10 分钟按交易日历补拉上一交易日。货币基金和 REIT 本期不走普通净值模型。
_Avoid_: 用 fundgz 作为净值发布门卫；把外部请求放在数据库事务内；只接受 navDate=今天而漏掉滞后基金；跨夜补拉仍固定与“今天”比较

**K 线图（Kline Chart）**:
行情工作台基金详情页 K 线,前端用 klinecharts v9(内置 MA/MACD/VOL 指标)。ETF/指数基金读 `index_kline` 本地缓存(行情指标刷新任务每日算量能状态时顺便落库,零额外请求)渲染日 K,
周/月 K 在日 K 上聚合(open=首日、high=max、low=min、close=末日、volume=sum)。主图蜡烛 + MA5/10/20/30(可开关),副图成交量(常驻)+ MACD(可切换)。主动/混合基金或缓存空且实时拉取失败时降级净值面积图。
**必须读本地缓存**:push2his.eastmoney.com 对按需高频请求 IP-blocks(http 000 "Unexpected end of file"),
图表按 view/切周期拉会触发限流;改读缓存后图表不再直连 push2his。缓存空(尚未同步)时实时拉作兜底。
`KlineService` period→klt(101/102/103)仅兜底用;`MarketDataSourceChain` **必须 override `fetchIndexKlineWithPeriod`** 透传 klt(接口 default 会忽略 klt 降级日K)。

**指数 K 线数据源(中证指数公司 csindex.com.cn)**:
借鉴 akshare `stock_zh_index_hist_csindex`,指数日 K 主源改为中证指数公司官方接口
`www.csindex.com.cn/csindex-home/perf/index-perf?indexCode={code}&startDate=...&endDate=...`(返回 OHLCV JSON,不封 IP、不要求 Referer)。
`CsindexMarketDataSource` 置于 `MarketDataSourceChain` 链首 [csindex, ths, eastmoney](issue #186 对齐实现):CSI 主题指数(930xxx,如 930713 中证人工智能)
与中证编制沪市指数(000300 沪深300、000016 上证50、000852 中证1000)由 csindex 命中,绕开被 VPS IP 限流的 push2his。csindex 或同花顺失败/空结果后由东财兜底最近日线。
csindex 仅提供日 K,周/月 K 在源内聚合(`CsindexJsParser.aggregate`,语义同 KlineService)。secid "2.930713"/"1.000300" 剥前缀取裸代码调 csindex。
深交所指数(399xxx)csindex 返空 data → 抛异常让链回退同花顺。csindex 不支持基金净值/字典,抛 `UnsupportedOperationException`,
`MarketDataSourceChain.tryEach` 对该异常静默跳过(不污染日志),直接回退同花顺。详见 ADR-0017。
_Avoid_: 指数 K 线仍走 push2his(VPS IP 被限流,http 000 永久失败,缓存无法填充陷入死循环);把 csindex 用于基金净值(它只发指数)

_Avoid_: 图表直连 push2his(触发 IP 限流);在缓存空时直接降级净值(应先实时拉兜底);后端算指标(klinecharts 内置,前端只喂 OHLCV)

## 盈亏与涨跌

**今日涨跌（Daily Change）**:
普通基金随自身估值时段演进三态——当日 `gztime` 尚未出现 = 0，使用最近一期已确认净值；当日 `gztime` 已出现且当日净值未落库 = fundgz 实时估算涨跌；当日净值已落库 = 当日累计净值 / 上一期累计净值 - 1。QDII 不要求最新净值日期等于今天：本地已有两期确认净值时，优先展示最新两期确认净值的实际涨跌，并返回真实 `valuationDate`；盘中估值不得覆盖该确认收益。估算涨跌来自 fundgz 的 gszzl（基于单位净值，但涨跌幅是比例，同日不除权时单位/累计净值涨跌幅一致）。观察池基金（无持仓）也看涨跌；普通基金空响应或失败时返回未知，不得显示昨日涨跌。
QDII 判定以持久化的 `fund.investment_target=QDII` 为准；新建/改名时名称含 QDII 会补齐该字段，V26 迁移回填存量空分类且不覆盖已有非空值。
_Avoid_: 普通基金把今日涨跌当"T-1 vs T-2"（那是昨日涨跌）；QDII 用页面日期冒充净值日期；前端估值接口覆盖后端已选择的 QDII 确认收益；用单位净值直接算复权涨跌比例（分红除权会失真）

**今日盈亏（Daily PnL）**:
普通基金随今日涨跌三态（盘前 0 / 盘中估算 / 盘后实际）；QDII 使用最新确认净值收益并标注实际净值日。算法 = 基准市值 × 涨跌幅：确认收益以持仓份额 × 上一期单位净值为基准，估算收益以持仓份额 × 最近确认单位净值为基准；涨跌幅使用累计净值复权比例。无持仓（观察池基金）今日盈亏为 null；组合今日盈亏只累加有当日数据的持仓，并通过覆盖数标明部分口径；全部持仓均无当日数据时才为未知。若未知由估值拉取失败导致，不能只显示普通 `-`，必须明确显示「估值拉取失败」及失败持仓基金数量。
_Avoid_: 用份额×(gsz-昨日单位净值) 算估算盈亏（单位净值口径，分红基金失真）

**持仓成本价（Cost Per Share）**:
每份基金的当前成本单价。建仓时用户可填（不填默认 T-1 净值）；后续 INCREASE/TRANSFER_IN/INVEST 交易 CONFIRMED 时加权更新（`新单价 = (旧单价×旧份额 + 本次amount) / 新旧份额之和`）。持仓期间手工修正会形成一次成本基准重置：按修正时的持仓份额和新单价建立基准，重置前的交易成本不再参与当前成本计算，重置后的买入继续加权。修正不追溯历史交易或 FIFO lot 的取得成本。卖出不改单价。清仓再入场时旧值自然被新交易覆盖。
_Avoid_: 持仓成本总额（旧定义已废弃，改成单价）；穿透交易表实时派生；用当前成本修正篡改历史已实现盈亏

**总盈亏（Total PnL）**:
基金整体赚了还是亏了。估值阶段开始前 = 持仓份额 ×（最近已确认单位净值 - 成本单价）；估值阶段 = 持仓份额 ×（最近已确认单位净值 × (1 + 今日涨跌幅) - 成本单价）；当日净值落库后 = 持仓份额 ×（当日单位净值 - 成本单价）。用于"盈利基金/亏损基金"分组。无持仓为 null；估值阶段已经开始但估值空响应或失败时，当前持仓市值与总盈亏为未知，不得拿上一期已公布净值冒充当前值。
_Avoid_: 把总盈亏和今日盈亏混为一谈（前者是累计，后者是单日）；使用累计净值计算真实份额、市值或成本（累计净值不是交易结算价格）

**上涨/下跌基金 vs 盈利/亏损基金**:
两个正交维度。"上涨/下跌"按今日涨跌幅分组（净值变动率 > 0 / < 0），"盈利/亏损"按总盈亏分组（市值 vs 成本）。一只基金可能今日上涨但
整体亏损，或今日下跌但整体盈利。概览页同时展示两个维度的计数。
_Avoid_: 用今日涨跌判断盈亏基金（今日涨不代表整体赚）

## 手动交易

**月度定投预算与仓位提醒（DCA Budget & Position Warning）**:
`monthlyDcaBudget` 是用户可选的月度现金流提示线，不是余额、入金累计或买入额度。它按北京时间自然月比较所有未取消的
`INVEST` 交易与当前有效计划尚未生成交易的本月实际执行日；未设置时仍展示已定投和本月剩余预计，但不显示进度或超额。每只基金用
`positionWarningEnabled` 和 `positionWarningRatio`（默认开启、30%，范围 1% 到 100%）提示当前确认持仓市值占全部确认持仓市值的比例。
该非拦截语义是 ADR-0021 对旧资金池和单基金硬上限的有意替代，不是待补的交易校验。
预算和提醒只影响 View/UI，绝不能进入 INCREASE/TRANSFER_IN/INVEST、初始持仓、转换或净值确认路径。
_Avoid_: 将预算当可用现金、将提醒线变成 `BusinessException`、用 PENDING 或未来计划计算当前仓位比例。

**手动交易（Manual Transaction）**:
不经过信号、用户直接录入的交易。复用 `FundTransactionEntity`，`signalLog = null`（由信号触发的交易才填该字段）。支持全部 7 类来源：
加仓（INCREASE）/减仓（DECREASE）/转入（TRANSFER_IN）/转出（TRANSFER_OUT）/定投（INVEST）/调增（ADJUST_IN）/调减（ADJUST_OUT）。买入写 amount、卖出写 shares，
走 NavConfirmJob 回填另一侧。与信号触发交易共用同一套账目和持仓聚合。手动卖出不经过 `evaluateSignal`，不卡 7 天硬约束（前端可提示）。
ADJUST 只修正事实份额：ADJUST_OUT 按 FIFO 缩减 open lot；ADJUST_IN 不建收费 lot，后续卖出未被 lot 覆盖的事实份额按零赎回费降级。
`FundTransactionEntity.tradeDate` 是业务交易发生时间，`createdDate` 仅是 Spring 审计创建时间。手动交易允许填写 `tradeDate`，
不填默认当前时间，未来时间拒绝；转换两腿必须使用同一 `tradeDate`。手动确认和自动确认都按 `tradeDate` 对应的北京时间自然日选择单位净值，
仅存量记录 `tradeDate` 为空时才回退 `createdDate`。
入口在基金详情页"交易流水" Tab 的"手动录入"按钮。
_Avoid_: 为手动交易单独建表（复用 FundTransactionEntity 即可，signalLog=null 已是领域模型预留的手动标识）；
用审计字段 `createdDate` 表示用户选择的交易发生日（保存时会被审计机制覆盖）

**初始持仓录入（Existing Position Onboarding）**:
新建基金时录入已有持仓的建仓动作。`FundCreateRequest.initialMarketValue` 有值即触发——状态流转对齐 BUILD 信号确认
（`FundStatus → HOLDING`、写 INCREASE 交易），但确认时机同步：`shares = initialMarketValue / T-1净值`，置 CONFIRMED，
不等 NavConfirmJob。`initialMarketValue` 是**入仓市值**（"现在值多少钱"），净值用 T-1（最近一期已公布）反算份额。
`costPerShare` 可选填（成本单价，不填默认 T-1 净值），>0 校验，存入 Accounting `Position.costPerShare` 作为当前成本基准；`FundEntity.costPerShare` 仅保留为 legacy 兼容字段，不再作为当前事实来源。
交易 `amount` 写 `initialMarketValue`（市值口径）。`openedAt` 用户可填（大致建仓时点，影响移动止盈持仓期高点起算），
不填用 now，须 ≤ 今天。与手动交易的本质区别：手动交易是已建仓后的资金动作（走 NavConfirmJob 异步确认），
初始持仓录入是建仓本身（同步确认）。交易来源用 INCREASE（对齐 handleBuild 建仓语义，不用 TRANSFER_IN——建仓是首笔买入非转入），
同步创建一条可供后续 FIFO 赎回的 open lot，但不重复扣申购费。`confirmTime` 与最终 `openedAt` 一致且不得为空。
无净值历史可反算时抛 `NAV_HISTORY_EMPTY` 不让建（同步确认的硬前提）；openedAt 晚于今天抛 `OPENED_AT_IN_FUTURE`；
`initialMarketValue` ≤ 0 或 `costPerShare` ≤ 0 抛参数校验错。
_Avoid_: 用昨日净值（语义模糊，最近一期已公布净值更准）；openedAt 用历史净值日期反算份额（金额是当前市值口径，
历史净值反算会让份额与当前市值对不上——openedAt 只标时间，不影响净值反算）

## 定投计划

**定投计划（DCA Plan）**:
用户配置一次、系统按周期自动买入的执行机制。**定投是自动执行,不是信号**——直接生成 `source=INVEST` 的 PENDING 交易,
完全绕开信号引擎（SignalLog）和卖出纪律。止盈交给基金绑定的移动止盈信号独立触发,与定投解耦。

`InvestmentPlan` 聚合（investmentplan 模块）承载:portfolioFundId / enabled / amount（基础金额）/ frequency(DAILY·日定投 / WEEKLY·周定投 / MONTHLY·月定投) /
dayOfWeek(1=周一..5=周五) / dayOfMonth(1-28,月定投日,封顶 28) / status / amountStrategy / referenceIndexCode / movingAverageDays。**新建即激活**:create 直接落 EFFECTIVE
(同基金已有 EFFECTIVE 则回退 DRAFT)。状态流转:EFFECTIVE --retire--> DRAFT --activate--> EFFECTIVE,
同基金同时最多一份 `EFFECTIVE`（数据库唯一约束兜底）。`enabled=false` 的 EFFECTIVE 计划 Job 跳过（暂停不绝育）。
只有 `DRAFT` 计划允许软删除；运行中或已暂停的 EFFECTIVE 必须先停用。删除计划不删除或改写任何历史/待确认交易。

**金额策略（amountStrategy）**:FIXED 固定金额按计划金额全额执行;LOW_VALUATION 指数估值百分位 ≤30% 才执行,
否则当日跳过;MOVING_AVERAGE 按指数收盘价相对 N 日均线偏离分档定率,下跌且近十日振幅 ≥5% 加倍档、<5% 减半档,
上涨按偏离程度递减;CHANGE_RATE 按(最新净值−平均成本)/平均成本 分档定率,浮盈越多买越少、浮亏越多买越多。
智能策略由 `SmartInvestmentAmountPolicy`（规则版本固化 `ALIPAY_2025_06_V1`,纯函数无 IO）计算实际金额,
事实数据（估值百分位/指数均线/振幅/净值/成本）经 `PlanInvestmentFactsGateway` 注入;数据不可用时当日跳过并落
`investment_plan_execution` 决策记录(SKIPPED + 原因码),不生成交易。

**InvestmentPlanExecutionJob**:cron `0 55 14 * * MON-FRI`(Asia/Shanghai),每个交易日 14:55 遍历 EFFECTIVE 计划逐计划独立事务执行。定投日判定:
日定投每个交易日都执行;周定投比对 day-of-week;月定投比对 day-of-month,计划日遇节假日顺延到下一个交易日补执行
（包括月末连续休市后跨月顺延；判定候选计划日至昨天均非交易日,则今天补）。新建月计划若创建时已错过本月计划日窗口,
首次执行自动顺延到下月(issue #158)。命中且 `enabled=true` 则生成 PENDING INVEST 交易
（FIXED 用计划金额,智能策略用决策金额,shares/nav 留空,`tradeDate`=实际执行日）。
**幂等**:同一计划同一北京时间自然日已有任意状态交易都跳过,月定投另查本月是否已生成任意账目或决策记录防止月内重复；
数据库原子插入兜底并发重跑。已确认表示本期完成，已撤销表示用户放弃本期，均不得重建。

**NavConfirmJob 时序**:14:55 定投下单(PENDING) → 20:00 DailyNavConfirmJob 拉当日净值 → 次日 03:00 NavConfirmJob 确认昨日 PENDING
（用下单日净值算 shares）。cron 从 `0 0 21 * *` 改 `0 0 3 * *`——凌晨确认的是"之前生成的"流水,单日定投流水在 14:55 已生成,
次日 3 点确认时净值已落地。日期统一按北京时间自然日映射为 UTC 00:00 标签；批量确认优先使用每笔交易的 `tradeDate`
确定净值日，Job 参数只作为旧数据缺失时间时的降级值，避免周末旧交易被下一交易日净值确认。
_Avoid_: 定投走信号引擎（信号是建议,定投是执行,语义不同）;月定投日 > 28;只按 PENDING 状态判断定投幂等
