# FundPilot 产品文档

> 版本：v0.0.1 · 单用户场景
> 定位：场外公募基金的**纪律策略执行平台**——帮用户把"基金纪律策略框架"机械执行下去，自动算信号、卡硬约束、留审计，最终操作仍由人工拍板。

---

## 一、产品是什么

### 1.1 一句话定位

**FundPilot = 基金纪律策略的"自动驾驶仪表盘"**。

它不是选基工具，不是估值工具，也不是自动交易机器人。它解决一个具体问题：

> "我知道要分档加仓、要顺势建仓、要移动止盈——但我每次临场都会手抖改阈值、会忘记该看哪些指标、会贪心不卖。FundPilot 每天替我把框架算一遍，告诉我今天该做什么，我只需确认执行。"

### 1.2 核心理念（来自框架原文）

| 原则 | 含义 | 在系统里的体现 |
|------|------|----------------|
| **触发用零滞后指标，调节用滞后指标** | 触发信号（回撤）要敏感怕错过，仓位调节（年线/MACD/量）要稳健怕被骗线 | 回撤算档位触发；年线/MACD/量只算系数不触发 |
| **所有信号都是提示，人工执行** | 系统只算、卡、记录，不替用户下单 | 每条信号需 `POST /operations` 确认才落地为交易 |
| **顺势思路** | 建仓顺势进、止盈顺势出、加仓用回撤左侧摊薄 | 建仓看趋势、止盈看回吐、加仓看回撤 |
| **不看估值** | 赚钱的强势基金估值普遍高，估值当禁买闸会挡住该买的 | 全程技术面调节，无估值指标 |
| **框架是死的，纪律是活的** | 参数定好后机械执行，不临场改阈值 | 参数走状态机（PENDING_CALIBRATION→CALIBRATED→EFFECTIVE），激活前必须过回测 |

### 1.3 用户画像与典型场景

**目标用户**：有一笔闲钱、想做场外基金纪律化投资，但执行纪律性差的个人投资者。

**典型一天**：
1. 14:50 系统自动跑行情拉取 + 信号生成。
2. 用户晚上打开 App，看到"待确认操作"工作台上有 2 条信号（一档加仓 + 移动止盈三档）。
3. 用户逐条点"确认"，填实际下单金额/份额 → 系统写交易记录、推进档位状态。
4. 当晚净值公布，NavConfirmJob 自动回填份额/金额/净值，交易转 CONFIRMED。

---

## 二、领域核心概念

理解 FundPilot 必须先分清这几个概念，它们是**两个正交的维度**，混在一起就会看不懂系统行为。

### 2.1 两条独立的生命周期线

一只基金同时挂在两条状态线上，互不干扰：

```
基金生命周期（FundStatus）          策略参数生命周期（StrategyParamStatus）
─────────────────────────          ──────────────────────────────────
PENDING_HOLDING (未建仓)            PENDING_CALIBRATION (待校准)
      │ 首次建仓确认                       │ 人工确认校准（自动跑回测）
      ▼                                    ▼
HOLDING (持仓中)                   CALIBRATED (已校准·带回测)
      │ 持仓份额归零                         │ 回测通过后人工激活
      ▼                                    ▼
CLEARED (已清仓)                   EFFECTIVE (已生效·出信号)
      │ 人工确认"继续观察"                    │ 新版激活时旧的回退 CALIBRATED
      ▼                              
PENDING_HOLDING (重新观察)          
```

**组合矩阵**（决定系统能做什么）：

| FundStatus | StrategyParamStatus | 能做什么 |
|------------|---------------------|----------|
| PENDING_HOLDING | 无 / 待校准 / 已校准 | 只能编辑参数，不出任何信号 |
| PENDING_HOLDING | **已生效** | 可推导**建仓**信号 |
| HOLDING | 无 / 待校准 / 已校准 | 不出信号 |
| HOLDING | **已生效** | 可推导**加仓 / 卖出**信号 |
| CLEARED | 任意 | 不出信号（人工确认后回 PENDING_HOLDING 重走流程） |

> **铁律：未绑定 EFFECTIVE 策略的基金，每日定时任务直接跳过，不落任何 SignalLog。** `evaluateSignal` 第一步就是查 EFFECTIVE 参数，没有就 short-circuit 返回 NONE。

### 2.2 两个不同维度的"动作"

| 概念 | 维度 | 取值 | 说明 |
|------|------|------|------|
| **SignalType（信号类型）** | 用户视角的策略意图 | NONE / BUILD / ADD / SELL | "系统建议你做什么策略动作" |
| **FundTransactionSource（交易来源）** | 账目层份额变化方向 | INCREASE / DECREASE / TRANSFER_IN / TRANSFER_OUT / INVEST | "账目份额怎么动" |

一只基金**当天信号 = 建仓**，但写成账目是 `INCREASE`（份额增加）。两者不能混用——信号是意图，来源是事实。

### 2.3 两个不同的"高点"（最关键的正交概念）

| 高点 | 定义 | 用途 | 存储方式 |
|------|------|------|----------|
| **前高 peakNav** | 基金历史最高累计净值 | 算**加仓档位**（市场便宜了多少） | 实时派生 `max(accumulated_nav)` |
| **持有期高点 holdingPeriodPeakNav** | 我建仓后该基金出现的最高累计净值 | 算**移动止盈**（我赚的钱回吐了多少） | 实时派生 `max(accumulated_nav) WHERE nav_date >= openedAt` |

> **为什么不存字段？** 派生值落库有三种失真风险：净值修正、补录历史、Job 异常。实时 `max()` 查询配索引毫秒级返回，且永远不会失真。详见 ADR-0001。

---

## 三、功能模块详解

FundPilot 由 7 个核心模块组成。下面逐个讲清楚**输入 → 逻辑 → 输出**。

### 模块 1：基金管理（Fund）

**职责**：管理"是哪只基金" + 基金属性 + 持有事实。

**关键字段**：

| 字段 | 说明 |
|------|------|
| `fundCode` / `fundName` | 基金代码 / 名称 |
| `fundCategory` | 宽基 / 行业 / 主动 / 混合——决定**默认档位和硬约束上限**（策略参数维度） |
| `fundSubType` | ETF / INDEX / INDEX_ENHANCED / ACTIVE——决定**行情数据源和逻辑止损判定路径**（数据源维度） |
| `benchmarkIndexCode` | 跟踪指数代码（如 `000300.SH`），指数类基金自动识别填入，主动类留空 |
| `status` | FundStatus（见 2.1） |
| `plannedTotalAmount` | 计划总仓位（纪律意图），建仓时设定，所有加仓/建仓额都按它的百分比算 |
| `openedAt` | 首次建仓时间，是持有期高点派生查询的起算点 |

**两类基金分类为何要分开**：
- `fundCategory`（宽基/行业/主动/混合）→ 决定加仓默认档位、单品种仓位上限（行业 15% vs 其他 20%）
- `fundSubType`（ETF/指数/指数增强/主动）→ 决定行情走哪条数据线、逻辑止损走哪条判定路径

> 漏网基金（自动识别没命中）兜底为 ACTIVE，用户可在建仓时手动补 `benchmarkIndexCode`。

**基金分类自动识别**（方法 A + C）：
- 名称关键词命中（"沪深300/中证500/创业板"等 → 映射对应指数代码）
- 命中失败兜底为 ACTIVE
- 本期**跳过**持仓股票与指数成分股重合度反推（方法 B，复杂度高，留给将来）

**归档 = 软删除**：
- 归档不是 FundStatus 的一部分，用 `@SoftDelete` 表达记录可见性
- 任意 FundStatus 都允许软删（包括 HOLDING），不卡校验
- 归档时关联数据（交易、信号、参数版本、净值历史）一起软删，恢复时一并恢复

---

### 模块 2：策略参数管理（FundStrategy）

**职责**：管理跟**策略参数版本相关**的字段。一只基金可同时存在多份参数（草稿/校准/生效共存），但任一时刻最多 1 份 EFFECTIVE。

**4 个可调参数**（`singlePositionLimit` 已升级为全局配置）：

| 参数 | 作用 |
|------|------|
| `tier1~4Drawdown` | 四档加仓回撤阈值（负数，如 -0.08 = 跌 8%） |
| `tier1~4Ratio` | 四档加仓比例（占 plannedTotalAmount 的百分比，**四档之和 ≤ 90%**） |
| `weeklyCoolDownThreshold` | 单周跌幅冷静阈值（触发强提示） |
| `stopLossPullbackPercent` | 移动止盈回落间隔（档位序号 × 此值 = 该档触发所需总回落） |

**运行时状态**（只在用户确认执行时写）：
- `tier1~4AddedAt`（方案B 四档实际执行时间戳，null = 未加）

**状态机**（StrategyParamStatus）：

```
PENDING_CALIBRATION ──calibrate(自动跑回测)──→ CALIBRATED ──activate(需 passed=true)──→ EFFECTIVE
       ↑                                          │                                          │
       └──────── CLEARED→PENDING_HOLDING 强制回退 ─┘         新版激活/主动停用 ←──── retire ──┘
                                                                  ↓
                                                              回退 CALIBRATED
```

**激活前置条件**：必须存在一份 `passed = true` 的回测结果。calibrate 时自动跑过去一年窗口回测，自带一份结果——`passed = true` 可直接激活，否则提示调参或换窗口重测。

**默认档位表**（按 fundCategory，差异化体现在回撤阈值上）：

| 档位 | 宽基 | 行业 | 主动 | 混合 |
|------|------|------|------|------|
| 一档 | -8% | -15% | -12% | -12% |
| 二档 | -15% | -25% | -20% | -22% |
| 三档 | -25% | -35% | -30% | -32% |
| 四档 | -35% | -45% | -40% | -40% |

**默认加仓比例**（四类基金共用一组金字塔比例）：
- 一档 15% / 二档 20% / 三档 25% / 四档 30%（合计 90%）
- 建仓首笔 10% + 四档 90% = 100% 计划总仓位，正好用满

> **建仓首笔 10% 是框架固定纪律，落代码常量 `BUILD_RATIO = 0.10`，不做可调参数**——否则破坏"建仓 10% + 四档 90% = 100%"的数学闭环。

**默认冷静阈值**：宽基 8% / 行业 12% / 主动 10% / 混合 10%（按波动率差异分档，行业最宽、宽基最紧）。

---

### 模块 3：信号引擎（DisciplineStrategyService）★核心

**职责**：纯函数 `evaluateSignal`，按九步流程对单只基金产出策略建议。**零 Spring/DB 依赖**——所有外部值由调用方预注入，便于单测。

**九步流程**：

```
┌─────────────────────────────────────────────────────────────┐
│ 步骤 1: 状态门控                                              │
│   CLEARED → NONE(FUND_CLEARED)                               │
│   PENDING_HOLDING → 只能 BUILD                                │
│   HOLDING → 可 ADD / SELL                                    │
├─────────────────────────────────────────────────────────────┤
│ 步骤 2: 策略生效检查                                           │
│   strategy == null 或 status ≠ EFFECTIVE → NONE(NO_STRATEGY) │
├─────────────────────────────────────────────────────────────┤
│ 步骤 3: 回撤派生（HOLDING 才算）                                │
│   drawdown = (currentNav - peakNav) / peakNav  负数表跌幅     │
├─────────────────────────────────────────────────────────────┤
│ 步骤 4: 反弹清空（HOLDING 才清）                                │
│   drawdown > tierNDrawdown - 0.5%（缓冲带）→ 清空更深档标记   │
│   warnings 记 TIER_CLEARED                                    │
├─────────────────────────────────────────────────────────────┤
│ 步骤 5: 决策动作                                              │
│   SELL 优先级: 逻辑止损 > 移动止盈 > 再平衡                    │
│   否则 ADD 档位; 否则 BUILD 三条件                             │
├─────────────────────────────────────────────────────────────┤
│ 步骤 6: 加 warnings（BUILD/ADD 专属）                         │
│   WEEKLY_COOLDOWN / BREAKDOWN_WATCH / INSUFFICIENT_DATA      │
├─────────────────────────────────────────────────────────────┤
│ 步骤 7: 硬约束（BUILD/ADD 才检查，违规则降级 NONE）             │
├─────────────────────────────────────────────────────────────┤
│ 步骤 8: MIN_HOLD_DAYS（SELL 非逻辑止损未满 5 交易日→降级 NONE） │
│   逻辑止损豁免但记 MIN_HOLD_DAYS_OVERRIDDEN                    │
├─────────────────────────────────────────────────────────────┤
│ 步骤 9: 组装 SignalResult                                     │
└─────────────────────────────────────────────────────────────┘
```

#### 3.1 建仓信号（BUILD）

**触发条件**（三条件**全部**满足，写死不参数化）：
1. 价格在年线上方
2. 年线向上
3. **今天累计净值 ≥ 最近 60 个交易日累计净值的最大值**（今天就是 60 日新高）

**建议金额** = `plannedTotalAmount × 0.10`

> 第三条收紧解读为"今天创新高"——非今天创新高是过去的信号，不应在今天出建议。

#### 3.2 加仓信号（ADD）

**机制：方案B——每档只加一次 + 反弹清空**

档位区划分（以宽基为例）：

| 回撤区间 | 档位 |
|----------|------|
| < 8% | 未触发区 |
| 8% ~ 15% | 一档 |
| 15% ~ 25% | 二档 |
| 25% ~ 35% | 三档 |
| ≥ 35% | 四档 |

**触发**：遍历 tier 1-4，`drawdown <= tierNDrawdown`（跌破阈值）且 `tierNAddedAt == null`（未触发过）→ 触发该档加仓。

**建议金额** = `plannedTotalAmount × tierRatio × 调节系数`（见模块 4）

**反弹清空**：净值回升变浅换到更浅档 → 清空所有比当前档更深的已加标记。
- 从二档反弹到一档区 → 清空二、三、四档标记
- 反弹到未触发区（< 一档阈值 - 0.5%）→ 清空全部标记，下一轮从一档重新开始

> **缓冲带 0.5% 只加在清空侧，加档侧精确触发**——防止净值在档位边界震荡导致"加-清-加"频繁抖动（ADR-0003）。

#### 3.3 卖出信号（SELL）

**优先级**：逻辑止损 > 移动止盈 > 再平衡。命中即返回，一只基金每日最多一类 SELL。

**① 逻辑破坏止损（LOGIC_BROKEN）**——趋势死亡型，一次清空：

按 `fundSubType` 分派判定条件（三条件**同时**命中）：

| 基金类型 | 条件① | 条件② | 条件③ |
|----------|-------|-------|-------|
| ETF/INDEX/INDEX_ENHANCED | 净值跌破年线 | 周 MACD 绿柱扩大 | **跟踪指数**放量下跌（当日量 > 20 日均量 × 1.5 且当日收跌） |
| ACTIVE | 净值跌破年线 | 周 MACD 绿柱扩大 | **单周跌幅 > weeklyCoolDownThreshold** |

触发后：当前持仓全卖，`tier1~4AddedAt` 全清，`FundStatus → CLEARED`，**突破 7 天内不赎回硬约束**（记 OVERRIDDEN）。

> 主动基金无跟踪指数、无真实成交量，用单周跌幅作"资金在撤"的代理信号。

**② 移动止盈（TRAILING_STOP）**——分档减仓，与"分档加仓"买卖镜像对称：

- 从 `holdingPeriodPeakNav` 回落 N × `stopLossPullbackPercent` 触发卖第 N 档（倒序：深档先卖）
- 默认 8% 时：回落 8%/16%/24%/32% 分别触发卖四档/三档/二档/一档+建仓
- **空档轮空**：从应触发档位往浅档找第一个 `tierNAddedAt` 不为 null 的档；都找不到 → NONE(NO_TIER_TO_SELL)
- **分档卖出份额（A1 规则）**：每档触发卖出的份额 = 该档加仓时**实际入账的份额**（买卖完全对称，自动吸收 override）
- 第四档触发时连卖建仓份额，归零 → `FundStatus → CLEARED`

**③ 再平衡减仓（REBALANCE）**——存量超限的被动卖出：

- 每日 14:50 检查：单只基金占比 = 实际持仓金额 / 总权益持仓金额 > `singlePositionLimit`
- 上限按 fundCategory：宽基/主动/混合 20%，行业 15%
- 卖出金额 = `(当前占比 - 上限) × 总权益持仓金额`，按最近净值反算为份额
- **遵守 7 天内不赎回硬约束**（不豁免）；触发后不清档位（持仓还在）

> 再平衡管"存量超限被动卖出"，硬约束管"主动加仓不能突破上限"——两套机制。

---

### 模块 4：调节层（决定加多少）

加仓触发后，用滞后指标给仓位打系数。不是 0/1 开关，而是乘数。

**调节指标与系数表**（照抄框架 §七，照抄不偏离）：

| 调节指标 | 看什么 | 系数 |
|----------|--------|------|
| **年线** | 上方且向上 | 1.0 |
| | 上方但向下 | 0.7 |
| | 下方且向下 | 0.4 |
| **周 MACD** | 底背离 | 1.2 |
| | 绿柱缩小（接近金叉） | 1.0 |
| | 红柱缩小 | 0.9 |
| | 绿柱扩大 | 0.6 |
| **成交量** | 地量企稳 | 1.2 |
| | 正常 | 1.0 |
| | 放量下跌 | 0.5 |

**合成规则**：
```
最终系数 = 年线系数 × MACD系数 × 成交量系数
最终系数 = clamp(最终系数, 0.3, 1.5)   # 防止极端值
实际加仓额 = 该档基础加仓额 × 最终系数
```

> 最低 0.3 意味着技术面全面走弱时仍加 30% 基础额——由"破位观望"和硬约束兜底。

**破位观望强提示（BREAKDOWN_WATCH）**——加仓信号专属强提示，不阻断：
- 当"年线下方且向下"（系数 0.4）**且**"放量下跌"（系数 0.5）同时命中 → `warnings` 加 `BREAKDOWN_WATCH`
- 恢复条件：周 MACD 出现底背离，或绿柱开始缩小，任一满足即解除

**单周跌幅冷静强提示（WEEKLY_COOLDOWN）**——加仓信号专属，看净值下跌速度：
- 算法：取最近 5 个交易日的累计净值（实际是 [T-5, T-1] 区间），算两点跌幅 `(5天前累计净值 - 最近累计净值) / 5天前累计净值`
- 超过 `weeklyCoolDownThreshold` → `warnings` 加 `WEEKLY_COOLDOWN` 强提示
- **不阻断加仓**，用户可 override
- 数据不足（净值历史 < 5 个交易日）→ `INSUFFICIENT_DATA_FOR_COOLDOWN` 提示

> **用累计净值不用单位净值**——分红除权会让单位净值跌幅"虚高"。

---

### 模块 5：硬约束（不可 override 的底线）

框架第九节的 6 条硬约束，落到代码里拆清归属：

| 约束 | 归属层 | 说明 |
|------|--------|------|
| 单品种仓位上限 20%（行业 15%） | 信号服务 | 加仓建议金额计算时直接截断；存量超限触发再平衡 SELL |
| 单类型仓位上限 30% | 信号服务 | 需要"同类型总仓位"聚合查询 |
| 总仓位上限 80% | 信号服务 | 分母 = `UserConfig.totalInvestableCapital` |
| 单次加仓 ≤ 剩余可用 50% | 信号服务 | 算完建议金额后再截断 |
| 7 天内不赎回 | 卖出信号 | 窗口 **5 个交易日**（非自然日 7 天）；逻辑止损豁免；手动卖出不卡 |
| 不借钱加仓 | 不入代码 | 纯纪律，不建模 |

**7 天内不赎回硬约束细节**：
- 起算点 = `max(openedAt, tier1~4AddedAt)`，每次加仓都重置
- 判定窗口为 **5 个交易日**（不是自然日 7 天，更贴近市场节奏，需 `trading_calendar` 表）
- 未满窗口时：移动止盈和再平衡降级为 `NONE, reason=MIN_HOLD_DAYS_NOT_MET`
- 逻辑止损豁免，照常出 SELL 但在 `hardConstraintBreaches` 记 `MIN_HOLD_DAYS_OVERRIDDEN`
- 手动卖出（不带 `signalLogId`）不经过 `evaluateSignal`，不卡此约束

**违约后果**：BUILD/ADD 命中硬约束 → 降级为 `NONE, reason=HARD_CONSTRAINT_BREACH`，`hardConstraintBreaches` 字段记录命中的具体约束。

---

### 模块 6：信号生成与操作确认

这是把"算出来的信号"变成"实际交易"的两个动作。

#### 6.1 信号生成（每日 14:50 定时任务）

```
14:30  MarketDataFetchJob.batch(0)  ──┐
14:40  MarketDataFetchJob.batch(1)  ──┼── 拉取行情指标落 market_indicator_snapshot 表
14:50  MarketDataFetchJob.batch(2)  ──┘    (按 fundId.hashCode() % 3 分批)
       │
       ▼
14:50  SignalGenerationJob.generateDailySignals(today)
       │
       │  遍历所有"绑定 EFFECTIVE 策略 + 未软删"的基金
       │  每只基金:
       │    1. 读 market_indicator_snapshot(缺→NONE+INSUFFICIENT_MARKET_DATA)
       │    2. 算 CapitalContext(峰值/占比/份额/资金)
       │    3. 调 evaluateSignal
       │    4. 覆盖式落 SignalLog(软删同日旧行+写新)
       │    5. 写回 fund_strategy 的 tierNAddedAt(反弹清空副作用)
       │
       └─ 单只基金异常 try/catch 不影响其他基金
```

**为什么要冻进 SignalLog 快照表**：14:50 的市场指标收盘后无法复算，必须当时落库。同时让"被动忽略"可追踪——`signal_type <> 'NONE'` 且无对应 FundTransaction 的行 = 系统提示了但用户没回应。

**唯一性约束**：`(fund_id, signal_date)` 在未软删记录里唯一——每只基金每个交易日最多一行（含 NONE）。

#### 6.2 操作确认（用户回应信号）

用户调 `POST /api/funds/{fundId}/operations`，按 `signalLogId` 指向被回应的信号，请求体带 `actualAmount`（买入）或 `actualShares`（卖出）。

**SignalOperationService 分派表**：

| SignalLog | 推进动作 |
|-----------|----------|
| BUILD | 写 INCREASE 交易(amount=actualAmount)；FundStatus→HOLDING；openedAt=now |
| ADD tierN | 写 INCREASE 交易；tierNAddedAt=now |
| SELL TRAILING_STOP tierN | 写 DECREASE 交易(shares=actualShares)；清 tierNAddedAt；N=4 且持仓归零→CLEARED |
| SELL LOGIC_BROKEN | 写 DECREASE 交易；清全部 tier1~4AddedAt；FundStatus→CLEARED |
| SELL REBALANCE | 写 DECREASE 交易；不清档位；不改 FundStatus |

**关键设计**：
- `confirmOperation` 不复算信号——建议侧字段已在 SignalLog 里
- 用户改了建议金额下单时，`FundTransaction.amount` 就是实际下单值，系统不留痕（不记录是否 override）
- 用户主动 SKIP 和不理会信号在数据上等价——都是"信号有行、FundTransaction 无对应"

---

### 模块 7：交易与净值确认（FundTransaction）

**职责**：承载基金交易事件——买入、卖出、转入、转出。

**交易时序与状态机**（场外基金现实是 14:50 信号 → 15:00 前下单 → 当晚净值公布）：

```
PENDING  ── NavConfirmJob 跑完 ──→  CONFIRMED    (买入回填 shares=amount/nav；卖出回填 amount=shares×nav)
   │
   └── 用户撤单 ──→  CANCELLED
```

**下单那一刻写入已知侧**：
- 买入：`amount` 已知、`shares=null`（净值未公布算不出）
- 卖出：`shares` 已知、`amount=null`
- `nav=null`、`status=PENDING`

**当晚 NavConfirmJob 跑**：查 FundNavHistoryEntity 拿当日净值，回填另一侧 + `nav` + `confirmTime`，转 CONFIRMED。回填后字段不再改变（事实账目回填后永不改变原则）。

**持仓计算**（实时聚合，不存快照）：
```
持仓份额 = Σ( shares × direction ) WHERE status = CONFIRMED    # INCREASE/TRANSFER_IN=+1, DECREASE/TRANSFER_OUT=-1
在途订单 = Σ( ... ) WHERE status = PENDING                      # 前端单独展示
持仓金额 = 持仓份额 × 当前净值                                   # 实时算，不存
```

**份额而非金额维护**：金额会随净值每日变动，份额只在买卖时才变。`持仓金额 = 持仓份额 × 当前净值` 实时算。

**基金转换**：两条交易（TRANSFER_OUT + TRANSFER_IN）通过 `relatedTransaction` 互指，撤单时一起 CANCELLED。

---

## 四、行情数据源（基础设施）

### 4.1 三条数据线

数据来自**东方财富/天天基金公开接口**（本期接入真实实现，不走半自动灌入，ADR-0002）：

| 数据线 | 接口 | 用途 |
|--------|------|------|
| 基金净值历史 | `pingzhongdata.js` 的 `Data_netWorthTrend` / `Data_ACWorthTrend` | 单位净值/累计净值序列 |
| 基金字典 | `fundcode_search.js`（全量约 2 万条） | fundSubType 和 benchmarkIndexCode 自动识别 |
| 指数 K 线 | `push2his.eastmoney.com` | 量能类指标（成交量状态） |

### 4.2 限流与降级

- **限流**：东方财富对 IP 有限速（约每秒 2-3 次），用 Semaphore/Bucket4j 节流，加 `Referer: https://fund.eastmoney.com/` 头避免反爬
- **分批拉取**：14:30/14:40/14:50 三批（按 `fundId.hashCode() % 3` 切片），防 14:50 集中拉跑不完
- **失败降级**：单只基金拉取抛异常时记日志继续，不影响其他基金；该基金当天不写 snapshot，后续 SignalGenerationJob 读不到时出 `NONE, reason=INSUFFICIENT_MARKET_DATA`
- **数据源降级链**：主源失败 → 备用源 → 缓存 → NONE 降级（编码规范要求）

### 4.3 表级缓存（不引入 Redis）

用 PostgreSQL 的 `market_indicator_snapshot` 表当缓存：
- 每只基金每日 14:50 拉一次落库
- 之后所有信号生成、用户查看建议都从这张表读，不再发外部请求
- Redis 缓存层留给未来

### 4.4 派生的指标

从原始净值序列派生出（落 snapshot 表前算好）：

| 指标 | 算法 |
|------|------|
| `priceAboveYearLine` | 最近累计净值 vs 250 日累计净值均线 |
| `yearLineRising` | 年线斜率方向 |
| `weeklyMacdState` | 周 MACD 状态（底背离/绿柱缩小/红柱缩小/绿柱扩大） |
| `volumeState` | 量能状态（地量企稳/正常/放量下跌），需指数 K 线 |
| `weeklyDropPercent` | 单周两点跌幅 |
| `isSixtyDayHigh` | 今天是否 60 日新高 |

---

## 五、回测引擎（激活前置门）

### 5.1 作用

`CALIBRATED → EFFECTIVE` 的前置条件：必须有一份回测结果同时跑赢三条基准线。calibrate 时自动跑过去一年窗口回测，CALIBRATED 自带一份结果。

### 5.2 通过判定（passed）

策略必须**同时**满足：
1. **收益条件**：策略收益率**严格大于**三条基准线收益率
   - 沪深300 同期
   - 一次性 all-in（期初全仓买入持有到期末）
   - 等额定投（按周/月定额买入）
2. **回撤条件**：策略最大回撤 **≤** all-in 同期最大回撤（用 ≤ 不用 <，避免临界值抖动）

> 收益条件是策略的价值证明（直接买基准就行，策略必须更强）；回撤条件是纪律策略的设计底线（分档加仓的回撤不该比一次性 all-in 还大，否则逻辑破产）。

### 5.3 窗口降级

基金成立不满一年时，`backtestStart` 降级为最早可用净值日期，不报错。`passed` 判定跟窗口长度脱钩。

### 5.4 不存的东西

- **不存 `initialCapital`**：策略参数是比例制，回测在比例空间里算，`plannedTotalAmount` 被约掉，与资金盘子绝对值无关
- **不存 `backtestDataSnapshot`**：净值序列是输入，接口接受入参，复跑重新传即可
- **回测只验单基金维度的硬约束**（单品种上限、单次加仓 ≤ 50%、7 天不赎回），跨基金维度（单类型、总仓位）留到运行时校验

### 5.5 同一版参数允许多次回测

参数没变但回测数据更新（窗口推移）可重复回测，只要任一份 `passed = true` 就可激活。

---

## 六、前端产品形态（React 多页面应用）

前端是 React + Ant Design + React Query 的多页面 SPA，路由结构：

| 路由 | 页面 | 功能 |
|------|------|------|
| `/` | DashboardPage | 仪表盘：KPI 概览 + 待确认操作 + 持仓基金 |
| `/funds` | FundsPage | 基金列表：CRUD + 归档 |
| `/funds/:fundId` | FundDetailPage | 基金详情：含行情/信号/策略/交易 Tab |
| `/signals` | SignalsPage | 信号列表 |
| `/confirm` | ConfirmPage | 操作确认工作台 |
| `/transactions` | TransactionsPage | 交易流水 |
| `/settings` | SettingsPage | 用户配置（总可投资金） |
| `/admin` | AdminPage | 管理后台：手动触发信号生成/净值确认/行情刷新 |

**关键交互模式**：
- Dashboard 顶部 4 个 KPI 卡片：待确认操作数、持仓基金数、总可投资金、计划仓位占比
- 待确认操作工作台：跨基金聚合所有未回应信号，点"去确认"跳转
- 基金详情页用 Tab 切换：行情指标 / 信号历史 / 策略参数（含回测）/ 交易流水
- 枚举值统一映射中文 label（`constants.js`），标签颜色按状态语义分（绿=成功态、金=进行中、红=终态/卖出）

---

## 七、完整使用流程

### 7.1 首次配置流程

```
1. 设置总可投资金（POST /api/user-config）
   └─ UserConfig.totalInvestableCapital = 用户整个账户的总可投资金（含未入场的现金）

2. 新建基金（POST /api/funds）
   └─ fundCode + fundCategory + plannedTotalAmount

3. 配置策略参数（POST /api/funds/{fundId}/strategies）
   └─ 创建 PENDING_CALIBRATION 草稿，填四档回撤/比例 + 冷静阈值 + 止盈回落

4. 校准参数（POST /api/strategies/{id}/calibrate）
   └─ 自动跑过去一年回测，落 StrategyBacktestEntity，状态→CALIBRATED

5. 激活参数（POST /api/strategies/{id}/activate）
   └─ 前置：存在 passed=true 的回测；状态→EFFECTIVE
   └─ 同只基金旧的 EFFECTIVE 自动回退 CALIBRATED
```

### 7.2 日常运营流程

```
14:30~14:50  系统自动拉取行情 + 生成信号（定时任务）
       │
       ▼
晚间    用户打开 App → Dashboard 看到"待确认操作"
       │
       ▼
       用户逐条确认（POST /api/funds/{fundId}/operations）
       └─ 买入带 actualAmount / 卖出带 actualShares
       └─ 系统写 PENDING 交易 + 推进档位/FundStatus
       │
       ▼
当晚    NavConfirmJob 自动跑
       └─ 回填 shares/amount/nav/confirmTime，交易→CONFIRMED
```

### 7.3 清仓后重新观察

```
FundStatus: CLEARED ──人工确认"继续观察"──→ PENDING_HOLDING
    │
    └─ 同时:该基金所有参数版本强制回退 PENDING_CALIBRATION
       (清仓是分水岭,旧校准和旧回测都不可信)
       openedAt 清空 → holdingPeriodPeakNav 派生查询自然失效
       tier1~4AddedAt 清空
    │
    ▼
重新走 calibrate → activate → BUILD 流程
```

---

## 八、API 端点清单

### 8.1 基金（FundController）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/funds` | 基金列表 |
| POST | `/api/funds` | 新建基金 |
| GET | `/api/funds/{id}` | 基金详情 |
| PUT | `/api/funds/{id}` | 更新基金 |
| DELETE | `/api/funds/{id}` | 归档（软删） |

### 8.2 策略（StrategyController）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/funds/{fundId}/strategies` | 某基金所有策略版本 |
| POST | `/api/funds/{fundId}/strategies` | 新建策略草稿 |
| PUT | `/api/strategies/{id}` | 更新草稿 |
| POST | `/api/strategies/{id}/calibrate` | 校准（自动回测） |
| POST | `/api/strategies/{id}/backtest` | 复跑回测 |
| GET | `/api/strategies/{id}/backtests` | 回测历史 |
| POST | `/api/strategies/{id}/activate` | 激活 |
| POST | `/api/strategies/{id}/retire` | 停用（回退 CALIBRATED） |
| GET | `/api/funds/{fundId}/strategies/active` | 当前生效参数 |

### 8.3 信号（SignalController / SignalOperationController）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/funds/{fundId}/signals/today` | 今日信号 |
| GET | `/api/funds/{fundId}/signals?from=&to=` | 历史信号区间查 |
| GET | `/api/signals/pending` | 跨基金未回应信号工作台 |
| POST | `/api/funds/{fundId}/operations` | 确认操作（写交易+推进状态） |

### 8.4 交易（TransactionCancelController）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/transactions/{id}/cancel` | 撤单（PENDING→CANCELLED） |

### 8.5 用户配置（UserConfigController）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/user-config` | 查总可投资金 |
| PUT | `/api/user-config` | 更新总可投资金 |

### 8.6 行情（MarketDataController）

| 方法 | 路径 | 功能 |
|------|------|------|
| GET | `/api/funds/{fundId}/market-indicators/today` | 今日行情指标 |

### 8.7 管理（AdminXxxController）

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | `/api/admin/signals/generate` | 手动触发信号生成 |
| POST | `/api/admin/transactions/confirm-nav` | 手动触发净值确认 |
| POST | `/api/admin/market-data/refresh` | 手动触发全量行情刷新 |

---

## 九、技术架构速览

### 9.1 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Spring Boot 4.0 + JPA + Flyway + PostgreSQL |
| 前端 | React + Vite + Ant Design + React Query + React Router |
| 数据源 | 东方财富/天天基金公开接口 |
| 缓存 | PostgreSQL `market_indicator_snapshot` 表（不引入 Redis） |

### 9.2 数据库表（10 张）

| 表 | 职责 |
|----|------|
| `fund` | 基金产品 + 属性 + 持有事实 |
| `fund_transaction` | 基金交易事件（改名自 fund_flow） |
| `fund_strategy` | 策略参数 + 该版本运行时状态 |
| `fund_strategy_activation` | 策略激活任期记账 |
| `signal_log` | 每日信号快照 |
| `strategy_backtest` | 回测结果 |
| `fund_nav_history` | 基金净值历史（peakNav 实时派生源） |
| `user_config` | 单用户配置（总可投资金） |
| `trading_calendar` | A 股交易日历（5 交易日窗口判定） |
| `market_indicator_snapshot` | 行情指标落库缓存 |

### 9.3 分层架构（编码规范要求）

```
Controller  ── 只做 HTTP 路由,不写业务逻辑,返回 View DTO
    │
Service     ── 业务编排（@RequiredArgsConstructor 注入）
    │
Support     ── 纯函数计算（HardConstraintChecker / CoefficientTable / BacktestSimulator）
    │
Repository  ── JPA 数据访问
    │
Entity      ── 领域模型（继承 AbstractEntity:软删+乐观锁+审计）
```

**关键编码规范**：
- Controller 不写逻辑，全部下沉 Service
- 全局用 Instant（不用 LocalDateTime）
- 魔法值枚举化（SignalReason / SignalWarning / FundSubType 等）
- ErrorCode 枚举统一异常
- 数据源降级链（主源 → 备用 → 缓存 → NONE）
- Actuator 健康检查
- View DTO 隔离 Entity 不直接暴露

---

## 十、本期边界与未来方向

### 10.1 本期已实现（v0.0.1）

- ✅ 基金/策略/信号/交易/用户配置/行情 全套 CRUD + 状态机
- ✅ 信号引擎九步流程（纯函数 evaluateSignal）
- ✅ 行情数据源真实接入（东方财富三条线）
- ✅ 回测引擎 + 三条基准线 + 通过判定
- ✅ 前端多页面应用 + API 接入
- ✅ 24 个后端端点
- ✅ 软删除 + 乐观锁 + 审计 + 全局异常处理

### 10.2 本期明确不做

- ❌ 多用户（本期单用户，不预留 userId）
- ❌ 持仓股票与指数成分股重合度反推（方法 B，留给将来）
- ❌ Redis 缓存层（表级缓存够用）
- ❌ 公告数据源（逻辑止损用技术面判定，不用基本面突变）
- ❌ 自动同步交易日历（人工维护）
- ❌ 定投（保留 INVEST 枚举值不用）
- ❌ 目标止盈 / 估值极端止盈（框架明确删除——卖飞强势品种/不看估值）

### 10.3 设计上的关键取舍（ADR 记录）

| ADR | 决策 | 理由 |
|-----|------|------|
| ADR-0001 | peakNav / holdingPeriodPeakNav 实时派生不存字段 | 派生值落库有净值修正/补录/Job 异常三种失真风险 |
| ADR-0002 | 本期接入东方财富真实行情源 | 不走半自动灌入，开发顺序数据源先行 |
| ADR-0003 | 反弹清空加 0.5% 缓冲带 | 偏离框架原文，但防止边界震荡导致"加-清-加"抖动 |
| ADR-0004 | SQL 限制代替软删除 | — |

---

## 附录：关键名词速查

| 名词 | 含义 |
|------|------|
| **BUILD_RATIO = 0.10** | 建仓首笔比例（计划总仓位 × 10%） |
| **TIER_CLEAR_BUFFER = 0.005** | 反弹清空缓冲带（0.5%） |
| **MIN_HOLD_DAYS = 5** | 持有期最少交易日数 |
| **方案B** | 加仓机制：每档只加一次 + 反弹清空 |
| **A1 规则** | 移动止盈分档卖出份额 = 该档加仓实际入账份额 |
| **B1 规则** | 移动止盈空档轮空（不跨档触发更浅档） |
| **peakNav** | 前高（基金历史最高累计净值，算加仓档位） |
| **holdingPeriodPeakNav** | 持有期高点（建仓后最高累计净值，算移动止盈） |
| **SignalLog** | 每日信号快照（每只基金每个交易日一行） |
| **Measure** | 值对象 `{value, measureUnit}`（AMOUNT=金额/SHARES=份额） |
| **CapitalContext** | 资金与仓位上下文（峰值/占比/份额/资金，evaluateSignal 入参） |
| **MarketIndicators** | 行情指标快照（年线/MACD/量/跌幅/新高，evaluateSignal 入参） |
