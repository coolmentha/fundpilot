# FundPilot · 基金纪律策略上下文

本上下文描述 FundPilot 后端"基金纪律策略"领域的核心术语与边界。它是领域语言的词典，不是规格说明书。实现细节、字段定义、状态机见
`backend/docs/` 下的设计文档。

## 信号与交易动作

**SignalType（信号类型）**:
系统每日 14:50 在第三批行情快照完成后，对绑定 `EFFECTIVE` 策略的基金评估并固化进 `SignalLogEntity`，站在**用户视角的策略动作**。四值：
`NONE`（无建议）/ `BUILD`（建仓）/ `ADD`（加仓）/ `SELL`（卖出）。
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
建仓后该基金出现的最高累计净值，是移动止盈判定的基准。**不存字段，实时派生**——
`max(fund_nav_history.accumulated_nav) WHERE nav_date >= fund.openedAt`。`FundStatus = HOLDING` 时才有意义；
`CLEARED → PENDING_HOLDING` 时因字段不存在，自然无需清理。
_Avoid_: 在 `FundEntity` 上存 `holdingPeriodPeakNav` 字段（同前高，派生值落库会失真）

## 卖出纪律

**移动止盈（Trailing Take-Profit）**:
从 `holdingPeriodPeakNav` 回落触发的**分档减仓**信号。回落 n×`stopLossPullbackPercent` 触发卖 `holdingShares × (n/4)`：
回落 1×阈值卖 1/4、2×卖 1/2、3×卖 3/4、4×全卖。不依赖金字塔档位状态（已解耦，ADR-0015）——份额基数是当前持仓 `holdingShares`，
回落越深卖越多。4×阈值全卖后若持仓归零，`FundStatus → CLEARED`。
_Avoid_: 一次性全清（失去渐进止盈语义）；按金字塔各档加仓份额卖（金字塔已退场）

**移动止盈触发阈值（stopLossPullbackPercent）**:
`FundStrategyEntity` 上唯一可配的策略参数。回落 n×此值 = 第 n 档触发。默认 -0.08（回落 8%）：回落 8%/16%/24%/32%
分别触发卖 1/4、1/2、3/4、全部。用户调一个值控制全部 4 档节奏。
_Avoid_: 把阈值做成多档可配（一个值控制全档节奏更简洁）

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
保护性约束，防止"刚买就卖"的短线反人性操作。起算点只取最近一次已确认买入类交易（INCREASE/TRANSFER_IN/INVEST）的 `confirmTime`，
每次买入都重置。判定窗口为 **5 个交易日**（不是自然日 7 天，更贴近市场节奏）。未满窗口时移动止盈降级为
`signalType=NONE, reason=MIN_HOLD_DAYS_NOT_MET`；逻辑止损豁免，照常出 SELL 信号但在 `warnings` 里记
`MIN_HOLD_DAYS_OVERRIDDEN`。手动卖出（不带 `signalLogId`）不经过 `evaluateSignal`，不卡此约束（前端可提示但不阻止）。
_Avoid_: 自然日 7 天（不贴市场节奏）；以 `openedAt` 为唯一起算点（后续买入的短线保护就失效了）

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
`FundStrategyEntity` 只剩 `status` + `stopLossPullbackPercent` 两个业务字段。

## 开发顺序

**数据源先行 / 策略主线后做**:
本期工程顺序严格串行：先把东方财富数据源完整做完（净值 + 字典 + 指数 K 线 + 自动识别 + 限流缓存 +
表级缓存），再开始策略主线（信号引擎、状态机、Service、Controller）。理由：策略主线开发时直接有真实数据可用，不用 mock；集成时无
DTO 不匹配返工；上线时没有"半手动灌入"的临时态要拆。代价是前期数小时看不到策略效果。
_Avoid_: 并行推进（B 选项，集成返工风险）；分层交付（C 选项，量能和逻辑止损被切到阶段二）

## 行情数据源

**行情源（MarketData Source）**:
东方财富/天天基金公开接口。本期就接入真实实现，不走半自动灌入。三条数据线：基金净值历史（`pingzhongdata.js` 的
`Data_netWorthTrend` / `Data_ACWorthTrend`）、基金字典（`fundcode_search.js` 全量约 2 万条）、指数 K 线（
`push2his.eastmoney.com`，用于量能指标）。
_Avoid_: "本期半自动灌入"的旧定位（已升级为真实接入）

**基金子类型（fundSubType）**:
数据源维度的基金分类，区别于策略参数维度的 `FundCategory`（宽基/行业/主动/混合）。四值：`ETF`（场内交易，可直接拿自身 K 线）/
`INDEX`（指数基金，看跟踪指数）/ `INDEX_ENHANCED`（指数增强，看跟踪指数）/ `ACTIVE`（主动管理，无跟踪指数）。自动识别只走名称启发式（方法
A），未命中兜底为 ACTIVE（方法 C）；**不做持仓股票与指数成分股重合度反推**（方法 B，本期跳过，留给将来）。
_Avoid_: 把 `fundSubType` 和 `FundCategory` 合并（用途不同：前者决定数据源和逻辑止损判定路径，后者决定默认档位和硬约束上限）

**跟踪/基准指数代码（benchmarkIndexCode）**:
`FundEntity` 上的可空字段。指数/ETF/指数增强基金填实际跟踪指数（如 `000300.SH`），主动/混合基金默认填沪深300 `000300.SH` 但*
*逻辑止损不使用**（主动基金走单周跌幅路径）。识别流程：名称关键词命中 → 命中失败兜底为 ACTIVE（方法 A + C）。空值降级时逻辑止损不出信号（
`signalType=NONE, reason=INSUFFICIENT_MARKET_DATA`）。漏网基金由用户在建仓时手动补 `benchmarkIndexCode`。
_Avoid_: 主动基金强制要求填跟踪指数（主动基金本质上没有跟踪标的）；本期做持仓股票重合度反推（方法 B，复杂度高，留给将来）

**基金类型自动识别（fundCategory 自动回填）**:
`fundCategory`（宽基/行业/主动/混合）与 `fundSubType` 一样按基金名称启发式识别，**尽力填 + 可覆盖**——识别不准时填最可能值（无关键词的指数类
默认宽基、无关键词的主动类默认主动），用户可在编辑时手动改，不留痕（沿用"override 不留痕"已确认条款）。不阻塞建仓流程，符合"所有信号都是提示"精神。
识别规则：指数类基金（ETF/INDEX/INDEX_ENHANCED）名称含宽基指数词（沪深300/中证500/创业板/上证50/科创50/中证1000）→ 宽基；含行业词
（半导体/医药/新能源/消费/军工/银行等）→ 行业；两者都没命中 → 宽基。主动类基金（ACTIVE）名称含"混合/灵活配置/平衡" → 混合；否则 → 主动。
`rawName`（东方财富"稳健成长型"等风格描述）**不参与 fundCategory 判定**——它描述投资风格不描述资产类别，无法可靠区分宽基 vs 行业。
_Avoid_: 把识别不准的字段留 null（fundCategory 为 null 时默认档位和硬约束上限查不出来，阻塞后续流程）；用 rawName 判 fundCategory

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
Redis 缓存层留给未来。
_Avoid_: 本期引入 Redis（增加基础设施复杂度）

**盘中估值（Intraday Estimate）**:
三态今日涨跌「盘中态」的数据源。来自东方财富 fundgz 接口（`fundgz.1234567.com.cn/js/{code}.js`），返回盘中估算净值（gsz）与估算涨跌幅（gszzl）。后台每 30 秒刷新全部未软删基金（含观察池）的内存缓存，请求链只读缓存，不落库——估值是短时态数据，收盘后失效。gszzl 基于单位净值，但涨跌幅是比例，同日不除权时与累计净值涨跌幅一致，直接用作估算涨跌幅无口径问题。拉取失败保留旧缓存或降级 empty。
_Avoid_: 把估算净值落 fund_nav_history（那是已结算净值表，估值是短时态）；用 gsz 算绝对盈亏（单位净值口径，分红基金失真，用市值×涨跌幅比例规避）

**当晚净值确认（Daily Nav Confirm）**:
让三态今日涨跌「盘后实际值」生效的机制。场外基金当日净值收盘后约 20:00 才公布（14:50 定时任务拉到的是 T-1 昨日净值）。每晚 20:00-23:00 每分钟轮询所有基金，查 fund_nav_history 最近 navDate ≠ 今天（未确认）→ fundgz 判 jzrq 是否 = 今天（轻量判定已公布）→ 是则调 pingzhongdata 拿累计净值落库。已确认跳过（天然停止条件，全部确认后空跑）。用 fundgz 判定 + pingzhongdata 落库双接口，保证落累计净值而非单位净值。
_Avoid_: 用 fundgz 的 dwjz（单位净值）落库（分红基金累计净值失真）；已确认基金重复拉取（浪费请求）

**K 线图（Kline Chart）**:
行情工作台基金详情页 K 线,前端用 klinecharts v9(内置 MA/MACD/VOL 指标)。ETF/指数基金读 `index_kline` 本地缓存(MarketDataFetchService 每日算 VolumeState 时顺便落库,零额外请求)渲染日 K,
周/月 K 在日 K 上聚合(open=首日、high=max、low=min、close=末日、volume=sum)。主图蜡烛 + MA5/10/20/30(可开关),副图成交量(常驻)+ MACD(可切换)。主动/混合基金或缓存空且实时拉取失败时降级净值面积图。
**必须读本地缓存**:push2his.eastmoney.com 对按需高频请求 IP-blocks(http 000 "Unexpected end of file"),
图表按 view/切周期拉会触发限流;改读缓存后图表不再直连 push2his。缓存空(尚未同步)时实时拉作兜底。
`KlineService` period→klt(101/102/103)仅兜底用;`MarketDataSourceChain` **必须 override `fetchIndexKlineWithPeriod`** 透传 klt(接口 default 会忽略 klt 降级日K)。

**指数 K 线数据源(中证指数公司 csindex.com.cn)**:
借鉴 akshare `stock_zh_index_hist_csindex`,指数日 K 主源改为中证指数公司官方接口
`www.csindex.com.cn/csindex-home/perf/index-perf?indexCode={code}&startDate=...&endDate=...`(返回 OHLCV JSON,不封 IP、不要求 Referer)。
`CsindexMarketDataSource` 置于 `MarketDataSourceChain` 链首 [csindex, eastmoney, ths]:CSI 主题指数(930xxx,如 930713 中证人工智能)
与中证编制沪市指数(000300 沪深300、000016 上证50、000852 中证1000)由 csindex 命中,绕开被 VPS IP 限流的 push2his。
csindex 仅提供日 K,周/月 K 在源内聚合(`CsindexJsParser.aggregate`,语义同 KlineService)。secid "2.930713"/"1.000300" 剥前缀取裸代码调 csindex。
深交所指数(399xxx)csindex 返空 data → 抛异常让链回退 eastmoney。csindex 不支持基金净值/字典,抛 `UnsupportedOperationException`,
`MarketDataSourceChain.tryEach` 对该异常静默跳过(不污染日志),直接回退 eastmoney。详见 ADR-0017。
_Avoid_: 指数 K 线仍走 push2his(VPS IP 被限流,http 000 永久失败,缓存无法填充陷入死循环);把 csindex 用于基金净值(它只发指数)

_Avoid_: 图表直连 push2his(触发 IP 限流);在缓存空时直接降级净值(应先实时拉兜底);后端算指标(klinecharts 内置,前端只喂 OHLCV)

## 盈亏与涨跌

**今日涨跌（Daily Change）**:
随交易时段演进的三态概念——盘前 = 0、盘中 = fundgz 实时估算涨跌、盘后 = 当日落库累计净值 / 昨日累计净值 - 1。判定靠"当前时间 + 当日净值是否已落库"（非纯时间）：盘后净值未公布时仍显示估值，避免错误的 0 或旧值。15:00-20:00 待公布时段显示盘中最后一次估值（带"估"标记），当晚净值落库后切换实际值。估算涨跌来自 fundgz 的 gszzl（基于单位净值，但涨跌幅是比例，同日不除权时单位/累计净值涨跌幅一致）。观察池基金（无持仓）也看涨跌三态。
_Avoid_: 把今日涨跌当"T-1 vs T-2"（那是昨日涨跌，旧定义已废弃）；用单位净值算绝对盈亏（口径分裂）

**今日盈亏（Daily PnL）**:
随今日涨跌三态（盘前 0 / 盘中估算 / 盘后实际）。算法 = 昨日市值 × 今日涨跌幅（昨日市值 = 持仓份额 × 上一期累计净值，是确定的基线；乘涨跌幅比例）。口径统一，不引入单位净值 gsz，规避分红基金单位/累计净值分裂。无持仓（观察池基金）今日盈亏为 null。
_Avoid_: 用份额×(gsz-昨日单位净值) 算估算盈亏（单位净值口径，分红基金失真）

**持仓成本价（Cost Per Share）**:
每份基金的成本单价，`FundEntity` 上的维护字段。建仓时用户可填（不填默认 T-1 净值）；后续 INCREASE/TRANSFER_IN/INVEST 交易 CONFIRMED 时同一事务内加权更新（`新单价 = (旧单价×旧份额 + 本次amount) / 新旧份额之和`）。卖出不改单价。清仓再入场时旧值自然被新交易覆盖。用于总盈亏计算。
_Avoid_: 持仓成本总额（旧定义已废弃，改成单价）；穿透交易表实时派生（成本价是持仓属性应存储，与 peakNav 等行情派生值不同）

**总盈亏（Total PnL）**:
基金整体赚了还是亏了。盘后 = 持仓份额 ×（最近累计净值 - 成本单价）；盘中估算 = 昨日总盈亏 × (1 + 今日涨跌幅)（口径与今日盈亏同源，都用涨跌幅比例推算）。用于"盈利基金/亏损基金"分组。无持仓为 null。
_Avoid_: 把总盈亏和今日盈亏混为一谈（前者是累计，后者是单日）；盘中总盈亏用单位净值 gsz 算（口径分裂）

**上涨/下跌基金 vs 盈利/亏损基金**:
两个正交维度。"上涨/下跌"按今日涨跌幅分组（净值变动率 > 0 / < 0），"盈利/亏损"按总盈亏分组（市值 vs 成本）。一只基金可能今日上涨但
整体亏损，或今日下跌但整体盈利。概览页同时展示两个维度的计数。
_Avoid_: 用今日涨跌判断盈亏基金（今日涨不代表整体赚）

## 手动交易

**手动交易（Manual Transaction）**:
不经过信号、用户直接录入的交易。复用 `FundTransactionEntity`，`signalLog = null`（由信号触发的交易才填该字段）。支持全部 7 类来源：
加仓（INCREASE）/减仓（DECREASE）/转入（TRANSFER_IN）/转出（TRANSFER_OUT）/定投（INVEST）/调增（ADJUST_IN）/调减（ADJUST_OUT）。买入写 amount、卖出写 shares，
走 NavConfirmJob 回填另一侧。与信号触发交易共用同一套账目和持仓聚合。手动卖出不经过 `evaluateSignal`，不卡 7 天硬约束（前端可提示）。
ADJUST 只修正事实份额：ADJUST_OUT 按 FIFO 缩减 open lot；ADJUST_IN 不建收费 lot，后续卖出未被 lot 覆盖的事实份额按零赎回费降级。
入口在基金详情页"交易流水" Tab 的"手动录入"按钮。
_Avoid_: 为手动交易单独建表（复用 FundTransactionEntity 即可，signalLog=null 已是领域模型预留的手动标识）

**初始持仓录入（Existing Position Onboarding）**:
新建基金时录入已有持仓的建仓动作。`FundCreateRequest.initialMarketValue` 有值即触发——状态流转对齐 BUILD 信号确认
（`FundStatus → HOLDING`、写 INCREASE 交易），但确认时机同步：`shares = initialMarketValue / T-1净值`，置 CONFIRMED，
不等 NavConfirmJob。`initialMarketValue` 是**入仓市值**（"现在值多少钱"），净值用 T-1（最近一期已公布）反算份额。
`costPerShare` 可选填（成本单价，不填默认 T-1 净值），>0 校验，存入 `FundEntity.costPerShare` 作为初始成本基准。
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

`FundDcaPlanEntity` 镜像 `FundStrategyEntity` 结构:fundEntity / enabled / amount / frequency(DAILY·日定投 / WEEKLY·周定投 / MONTHLY·月定投) /
dayOfWeek(1=周一..7=周日) / dayOfMonth(1-28,月定投日,封顶 28 避开月末) / status。**新建即激活**:create 直接落 EFFECTIVE
(同基金已有 EFFECTIVE 则回退 DRAFT)。状态流转:EFFECTIVE --retire--> DRAFT --activate--> EFFECTIVE,
同基金同时最多一份 `EFFECTIVE`（数据库 `uq_fund_dca_plan_effective` 兜底）。`enabled=false` 的 EFFECTIVE 计划 Job 跳过（暂停不绝育）。

**DcaSuggestionJob**:cron `0 55 14 * * MON-FRI`,每个交易日 14:55 遍历所有 EFFECTIVE 计划。定投日判定:
日定投每个交易日都执行;周定投比对 day-of-week;月定投比对 day-of-month,计划日遇节假日顺延到下一个交易日补执行
（判定:planDom..today-1 区间全非交易日,则今天补）。命中且 `enabled=true` 则生成 PENDING INVEST 交易（amount=计划金额,shares/nav 留空）。
**幂等**:同日同计划已有 PENDING 交易则跳过（`FundTransactionEntity.dcaPlanId` + `existsByDcaPlanIdAndStatusAndCreatedDateBetween` 兜底防重跑）。

**NavConfirmJob 时序**:14:55 定投下单(PENDING) → 20:00 DailyNavConfirmJob 拉当日净值 → 次日 03:00 NavConfirmJob 确认昨日 PENDING
（用下单日净值算 shares）。cron 从 `0 0 21 * *` 改 `0 0 3 * *`——凌晨确认的是"之前生成的"流水,单日定投流水在 14:55 已生成,
次日 3 点确认时净值已落地。日期统一按北京时间自然日映射为 UTC 00:00 标签；批量确认优先使用每笔交易的 `createdDate`
确定净值日，Job 参数只作为旧数据缺失时间时的降级值，避免周末旧交易被下一交易日净值确认。
_Avoid_: 定投走信号引擎（信号是建议,定投是执行,语义不同）;月定投日 > 28（避开月末交易日缺失,强制顺延增加复杂度）
