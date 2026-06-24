# FundPilot · 基金纪律策略上下文

本上下文描述 FundPilot 后端"基金纪律策略"领域的核心术语与边界。它是领域语言的词典，不是规格说明书。实现细节、字段定义、状态机见
`backend/docs/` 下的设计文档。

## 信号与交易动作

**SignalType（信号类型）**:
系统每日 14:50 对绑定 `EFFECTIVE` 策略的基金评估后冻进 `SignalLogEntity` 的策略意图，站在**用户视角的策略动作**。四值：
`NONE`（无建议）/ `BUILD`（建仓）/ `ADD`（加仓）/ `SELL`（卖出）。
_Avoid_: DECREASE（DECREASE 是账目方向，不是策略意图）

**FundTransactionSource（交易来源）**:
账目层份额变化方向的中性描述，五值：`INCREASE` / `DECREASE` / `TRANSFER_IN` / `TRANSFER_OUT` / `INVEST`。与 `SignalType` 是
**两个不同维度**——信号描述"系统建议什么策略动作"，来源描述"账目份额怎么动"。一只基金的交易来源沿用现有五值不收敛。
_Avoid_: BUY / SELL（语义已被 SignalType 占用，避免歧义）

## 仓位结构

**建仓首笔比例（BUILD_RATIO）**:
建仓信号建议金额 = `plannedTotalAmount × 0.10`。这是框架固定纪律，不是可调参数，落代码常量。与四档加仓比例（15/20/25/30 =
90%）构成 100% 计划总仓位的数学闭环。
_Avoid_: 首笔比例做成 `FundStrategyEntity` 可调字段（会破坏闭环）

**四档加仓比例（TierRatio）**:
四档加仓占 `plannedTotalAmount` 的百分比，四档之和 ≤ 90%。四类基金共用同一组金字塔比例（一档 15% / 二档 20% / 三档 25% / 四档
30%），差异化体现在回撤阈值上，不在比例上。
_Avoid_: 固定金额（tier1~4Amount 字段已废弃）

## 回撤基准

**前高（peakNav）**:
基金历史最高累计净值，是加仓档位判定的基准。**不存字段，实时派生**——`max(fund_nav_history.accumulated_nav)`，配
`(fund_id, nav_date)` 索引毫秒级返回。净值修正、补录历史、job 异常都不会导致失真。
_Avoid_: 在 `FundEntity` 上存 `peakNav` 字段（派生值落库会失真，见 ADR-0001）

**持有期高点（holdingPeriodPeakNav）**:
我建仓后该基金出现的最高累计净值，是移动止盈判定的基准。**不存字段，实时派生**——
`max(fund_nav_history.accumulated_nav) WHERE nav_date >= fund.openedAt`。`FundStatus = HOLDING` 时才有意义；
`CLEARED → PENDING_HOLDING` 时因字段不存在，自然无需清理。
_Avoid_: 在 `FundEntity` 上存 `holdingPeriodPeakNav` 字段（同前高，派生值落库会失真）

**回撤基准的职责划分**:
加仓档位用前高（市场便宜了多少），移动止盈用持有期高点（我赚的钱回吐了多少）。两个高点正交——前者服务买入纪律，后者服务卖出纪律。建仓信号不直接看回撤，看择时指标（60
日新高 / 年线 / MACD）。
_Avoid_: 加仓档位用持有期高点算（会让建仓时机决定加仓深度，破坏框架语义）

## 卖出纪律

**移动止盈（Trailing Take-Profit）**:
从 `holdingPeriodPeakNav` 回落触发的**分档减仓**
信号。买卖镜像对称——四档加仓正序进、四档止盈倒序出（深档先卖）。最深档（四档加仓）回落最浅时先平，回落进一步扩大才平更浅档，最后一档清掉建仓 +
一档加仓后归零，`FundStatus → CLEARED`。
_Avoid_: 一次性全清（trailing stop 的常见做法，但与"分档加仓"的对称性破坏）

**移动止盈触发阈值（stopLossPullbackPercent）**:
**每档分档减仓的回落间隔**，不是单次触发阈值。档位序号 × 此值 = 该档触发所需的总回落。默认 8% 时：回落 8%/16%/24%/32%
分别触发卖四档/三档/二档/一档+建仓。用户调一个值控制全部 4 档节奏。
_Avoid_: "单次触发阈值"的旧解读（B 选项确认前的版本，已废弃）

**分档卖出份额（A1 规则）**:
每档触发卖出的份额 = 该档加仓时实际入账的份额，即
`FundTransaction WHERE signalLog.signalType=ADD AND signalLog.triggerTier=N AND status=CONFIRMED` 那条交易的 `shares`
。买卖完全对称，自动吸收用户加仓时的 override（实际买多少就卖多少）。第四档触发时还要把建仓那条 `signalLog.signalType=BUILD`
的 `shares` 一起卖，归零进入 `CLEARED`。
_Avoid_: 按比例卖（30%/25%/20%/15% × 当前总份额）——会和实际入账份额对不上

**空档轮空（B1 规则）**:
用户跳过的档位（`tierNAddedAt` 永远是 null）在止盈时跳过，不跨档触发更浅档。引擎从"应触发档位"往浅档找第一个 `tierNAddedAt`
不为 null 的档；都找不到 → `signalType=NONE, reason=NO_TIER_TO_SELL`。卖出成功后 `tierNAddedAt` 清回 null，标记该档已平。
_Avoid_: 跳档触发更浅档（节奏被打乱，用户搞不清下次触发点）

**反弹清空（方案B 加仓状态机）**:
净值反弹使档位变浅时，清空所有比当前档更深的已加标记（`tierNAddedAt` 置 null）。清空只动字段不动交易——已买入份额不变，只影响下次该档触发判定。当前回撤
`< tier1Drawdown` 时清空全部四档标记，下轮从一档重新开始。SignalLog 的 `warnings` 里记 `TIER_CLEARED` 提示用户哪些档被清空了。
_Avoid_: 清空动作单独产生 SignalLog 行（它是状态机自动行为不是策略建议，只在 warnings 里留痕）

**反弹清空缓冲带（TIER_CLEAR_BUFFER = 0.5%）**:
清空判定**比框架阈值浅 0.5% 才触发**——加档侧保持精确（`drawdown >= tierNDrawdown` 即加），只在清空侧加缓冲。逐档检查：若
`drawdown < tierNDrawdown - 0.005` 则该档被"真正"脱离，清空它及更深的档。**偏离框架原文**
（框架是精确边界清空），加缓冲为防止边界震荡导致"加-清-加"频繁抖动。详见 ADR-0003。
_Avoid_: 两侧都加缓冲（Z2-c，会延迟加档信号）；加档侧加缓冲（Z2-b，违背"越跌越买"精神）

**逻辑破坏止损（Logic-Broken Stop-Loss）**:
趋势死亡型止损，和移动止盈完全不同——不分档、一次清空。触发后 `tier1~4AddedAt` 全清，`FundStatus → CLEARED`，**突破 7 天内不赎回硬约束
**。两类基金判定条件不同（按 `fundSubType` 分派）。
_Avoid_: 用基本面突变（基金经理变更等）判定——本期无公告数据源，等下一期接公告源再做

**逻辑止损 · ETF/指数/指数增强基金判定**:
三个条件**同时**命中才触发：① 净值跌破年线（最近累计净值 < 250 日累计净值均线）② 周 MACD 绿柱扩大 ③ **跟踪指数**
放量下跌（当日成交量 > 20 日均量 × 1.5 且当日收盘跌）。跟踪指数取 `FundEntity.benchmarkIndexCode`。
_Avoid_: 用基金自身净值算量能——基金没有成交量

**逻辑止损 · 主动/混合基金判定**:
三个条件**同时**命中才触发：① 净值跌破年线 ② 周 MACD 绿柱扩大 ③ **单周跌幅 > weeklyCoolDownThreshold**
。主动基金无跟踪指数、无真实成交量，用单周跌幅作"资金在撤"的代理信号。复用已有字段，不引入新数据源。
_Avoid_: 用沪深300 量能代理（反映大盘情绪不反映个股层面）或持仓股聚合量能（数据滞后一季度，实战价值打折）

**再平衡减仓（Rebalance）**:
存量超限的被动卖出，区别于硬约束（管"主动加仓不能突破上限"）。每日 14:50 检查：单只基金占比 = 实际持仓金额 / 总权益持仓金额 >
`singlePositionLimit`（宽基/主动/混合 20%，行业 15%）→ 触发。卖出金额 = `(当前占比 - 上限) × 总权益持仓金额`，按最近净值反算为份额。
**遵守 7 天内不赎回硬约束**（不豁免）；触发后不清档位（持仓还在）。
_Avoid_: 用单类型 30% 或总仓位 80% 触发再平衡（那两个上限是加仓时的硬约束，不在再平衡触发范围内）

**SELL 信号优先级**:
一只基金每日一行 SignalLog，SELL 信号最多一类。`evaluateSignal` 按"**逻辑止损 > 移动止盈 > 再平衡**"顺序检查，命中即返回。
`reason` 三值：`LOGIC_BROKEN` / `TRAILING_STOP` / `REBALANCE`。
_Avoid_: 同日多类型 SELL 信号叠加（违反"一只基金每日一行"的唯一性约束）

**7 天内不赎回硬约束（MIN_HOLD_DAYS）**:
保护性约束，防止"刚买就卖"的短线反人性操作。起算点取每次买入的 `confirmTime` 的最大值——
`max(openedAt, tier1AddedAt, tier2AddedAt, tier3AddedAt, tier4AddedAt)`，每次加仓都重置。判定窗口为 **5 个交易日**（不是自然日
7 天，更贴近市场节奏）。未满窗口时移动止盈和再平衡降级为 `signalType=NONE, reason=MIN_HOLD_DAYS_NOT_MET`；逻辑止损豁免，照常出
SELL 信号但在 `hardConstraintBreaches` 里记一条 `MIN_HOLD_DAYS_OVERRIDDEN`。手动卖出（不带 `signalLogId`）不经过
`evaluateSignal`，不卡此约束（前端可提示但不阻止）。
_Avoid_: 自然日 7 天（不贴市场节奏）；以 `openedAt` 为唯一起算点（加仓后的短线保护就失效了）

**交易日历（TradingCalendar）**:
`MIN_HOLD_DAYS` 判定 5 个交易日所需的基础数据表，记录每个日期是否为 A 股交易日（含节假日剔除）。一次性灌入未来几年的日历即可（A
股节假日规则相对固定），或定期从交易所日历同步。本期暂不做自动同步，人工维护。

**单周跌幅冷静（Weekly Cooldown）**:
加仓信号专属的强提示，看净值下跌速度。算法：取最近 5 个交易日的累计净值（含今日，但 14:50
信号生成时今日净值未公布，实际是 [T-5, T-1] 区间），算两点跌幅 `(5天前累计净值 - 最近累计净值) / 5天前累计净值`，超过
`weeklyCoolDownThreshold` 时在加仓信号的 `warnings` 里加 `WEEKLY_COOLDOWN` 强提示。**不阻断加仓**，用户可
override。主动基金的逻辑止损判定也复用同一算法。
_Avoid_: 用窗口内最大回撤（A2，会和移动止盈/档位判定语义重叠）；用单位净值 `nav`（分红除权会让跌幅"虚高"，所有回撤类计算统一用累计净值
`accumulatedNav`）

**冷静数据不足降级**:
新建仓基金净值历史不足 5 个交易日的，冷静判定降级为 `warnings` 里的 `INSUFFICIENT_DATA_FOR_COOLDOWN`
提示，不阻断加仓信号生成。透明告知用户而非严苛封死。
_Avoid_: 数据不足就完全不出加仓信号（D3，违背"建仓后即可看加仓信号"的设计）

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

## 调节系数

**调节系数表（CoefficientTable）**:
加仓建议额度的乘数，按框架 §七 原文照抄。三维度独立打分相乘再 clamp(0.3, 1.5)。年线 3 档：年线上方且向上 = 1.0 /
上方但向下 = 0.7 / 下方且向下 = 0.4。周 MACD 4 档：底背离 = 1.2 / 绿柱缩小 = 1.0 / 红柱缩小 = 0.9 / 绿柱扩大 = 0.6。成交量
3 档：地量企稳 = 1.2 / 正常 = 1.0 / 放量下跌 = 0.5。最终系数 = 年线 × MACD × 成交量，clamp(0.3, 1.5)。实际加仓额 =
基础加仓额 × 最终系数。
_Avoid_: 自定义系数表（无理由偏离框架原文）

**破位观望强提示**:
加仓信号专属强提示，不阻断信号生成。当"年线下方且向下"（系数 0.4）**且**"放量下跌"（系数 0.5）同时命中时，`warnings` 加
`BREAKDOWN_WATCH`："趋势破位 + 放量出逃，等恐慌释放完再加"。恢复条件：周 MACD 出现底背离，或绿柱开始缩小，任一满足即解除。
_Avoid_: 把破位观望做成硬约束（框架明确是强提示，可 override）

## 建仓信号

**建仓触发条件（写死不参数化）**:
三条件**全部**满足才出 BUILD 信号：① 价格在年线上方 ② 年线向上 ③ **今天累计净值 ≥ 最近 60 个交易日累计净值的最大值**（即"
今天就是 60 日新高"）。第三条收紧解读为"今天创新高"，非今天创新高是过去的信号，不应在今天出建议。
_Avoid_: 把"近 60 日创过阶段新高"解读为"过去 60 日内某天创过新高"（会延迟信号）；把建仓条件参数化（框架明确写死）

## 总仓位硬约束

**总可投资金（UserConfig.totalInvestableCapital）**:
硬约束 #3（总仓位 ≤ 80%）的分母来源，用户整个账户的总可投资金额（含未入场的现金、其他基金等）。本期单用户场景下新增
`user_config` 表，只一行，用户首次配置时手动填。非某只基金的 `plannedTotalAmount` 加总（那是单基金的纪律意图，不是账户可投资金）。
_Avoid_: 用 `Σ plannedTotalAmount ÷ 0.8` 反推（语义不对）；本期跳过总仓位硬约束（硬约束是框架核心，不能省）

## 行情数据缓存

**表级缓存（MarketIndicatorSnapshot）**:
不引入 Redis，用 PostgreSQL 的 `market_indicator_snapshot` 表当缓存。每只基金每日 14:50
拉一次落库，之后所有信号生成、用户查看建议都从这张表读，不再发外部请求。若 14:50 集中拉跑不完，加 `@Scheduled` 分批触发（如
14:30 / 14:40 / 14:50 三批）。Redis 缓存层留给未来。
_Avoid_: 本期引入 Redis（增加基础设施复杂度）
