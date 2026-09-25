# B-6 纪律判定 → 提醒（alerting）迁移离线等价性报告

> 版本：v0.14.0　子任务：B-6　对应计划：`docs/versions/v0.14.0-plan.md` §3.2 B-6、§5
> 比对脚本（归档副本）：`DisciplineMigrationEquivalenceTest.java`（同目录）
> 原始位置：`backend/src/test/java/com/fundpilot/backend/config/DisciplineMigrationEquivalenceTest.java`
> 运行命令：
> ```powershell
> $env:JAVA_TOOL_OPTIONS="-Duser.language=en -Duser.country=US"
> .\mvnw.cmd -o test "-Dtest=DisciplineMigrationEquivalenceTest" "-Dsurefire.failIfNoSpecifiedTests=false"
> ```
> 运行结果：`Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` → **BUILD SUCCESS**（控制台留证 `backend/b6-run.log`）

---

## 1. 比对设计

新旧两侧都跑**真实生产代码**，不使用复制的伪实现：

| 侧 | 逻辑破坏止损 | 回撤止盈 |
| --- | --- | --- |
| 旧（discipline） | `AdvicePolicy.evaluate(...)`，判据在 `AdvicePolicy.logicBroken`（跌破年线 且 周线 MACD 绿柱扩大 且（主动型 或 量能 HIGH_DROP）） | `AdvicePolicy.evaluate(...)` 的止盈分支 + `DisciplineStrategy.prepareTakeProfit` |
| 新（alerting） | `ConditionEvaluator.satisfied` + `ConditionEvaluationService.satisfied/detail`，条件组为 `PRICE_VS_MA BELOW 0(window=250)` + `WEEKLY_MACD_HISTOGRAM BELOW 0(12/26/9)` + `WEEKLY_MACD_HISTOGRAM DECREASING(12/26/9)`；非 ACTIVE 追加 `VOLUME_DROP ABOVE 1.5(window=20)`；随后按 `AlertSuggestionService.logicBroken` 的同序判定（含 `positive(holdingShares)` 前置校验） | `TakeProfitPolicy`（`holdingCost/floatingProfit/overallReturn/pullback/suggestedShares`）+ `SuggestionState.prepareTakeProfit` + `TakeProfitParams`，按 `AlertSuggestionService.trailingStop` 的同序判定 |

比对口径：①「是否给出卖出」是否逐一相等；②（仅测试 B）卖出时的**建议卖出份额**是否相等。

### 1.1 映射表一：逻辑破坏止损的状态 → 指标数值序列

映射只保留两侧判定真正依赖的语义，不追求数值与生产计算一致。

| 旧侧状态 | 新侧 `PRICE_VS_MA`（年线偏离率） |
| --- | --- |
| `priceAboveYearLine=false` | `[-0.01]`（低于均线） |
| `priceAboveYearLine=true` | `[0.01]` |
| `priceAboveYearLine=null` | 空序列（无数据，两侧都按「不满足」处理） |

| 旧侧 `MacdState` | 新侧 `WEEKLY_MACD_HISTOGRAM`（周线 MACD 柱，两期） |
| --- | --- |
| `GREEN_EXPANDING` | `[-0.02, -0.05]`（为负且较上周更小） |
| `GREEN_SHRINKING` | `[-0.05, -0.02]` |
| `RED_EXPANDING` | `[0.02, 0.05]` |
| `RED_SHRINKING` | `[0.05, 0.02]` |
| `null` | 空序列 |

| 旧侧 `VolumeState` | 新侧 `VOLUME_DROP`（放量下跌量比） |
| --- | --- |
| `HIGH_DROP` | `[1.8]`（≥ 1.5） |
| `NORMAL` | `[1.0]` |
| `LOW_STABLE` | `[0.4]` |
| `null` | 空序列 |

`productType=ACTIVE` 时新侧不参与 `VOLUME_DROP` 条件，与旧侧 `productType == ACTIVE` 的豁免一致。

### 1.2 映射表二：回撤止盈的参数与状态对应

| 旧侧（`DisciplineStrategy`） | 新侧 |
| --- | --- |
| 六个参数（`activation/pullback/harvest/minimumHolding/maxSingleSell/cooldownDays`） | `TakeProfitParams` 同名字段 |
| `takeProfitPhase`（`ACCUMULATING/ARMED/TRIGGERED/COOLDOWN`） | `SuggestionState.phase()`（同名枚举） |
| `cyclePeakNav` | `SuggestionState.cyclePeakNav()` |
| `cooldownStartedAt` | `SuggestionState.cooldownStartedAt()` |
| `cooldownFinished`（由 `cooldownStartedAt` 与 `cooldownDays` 算出） | 同一算式（测试逐组合断言两侧算式结果相等） |

四档预设：宽基 `0.15/0.06/0.5/0.5`、行业 `0.20/0.08/0.5/0.4`、主动 `0.15/0.07/0.5/0.5`、混合 `0.12/0.05/0.4/0.6`；统一 `maxSingleSell=0.2`、`cooldownDays=10`。

---

## 2. 三个测试的枚举规模与结论

### 测试 A：逻辑破坏止损全状态空间等价

- 枚举规模：`4 产品类型 × 3 年线位置 × 5 MACD 状态 × 4 量能状态 × 3 持仓状态 × 2 份额档` = **1440 组合**
- 旧侧命中（`action == SELL`）：**14**
- 新侧命中：**7**
- **可达组合差异：0**
- **不可达组合差异：7**（逐条见 §3）

### 测试 B：回撤止盈全参数网格等价

- 枚举规模：`4 预设 × 2 成本 × 2 份额 × 3 单位净值 × 3 累计净值 × 4 周期峰值 × 3 成熟份额 × 5 阶段` = **8640 组合**
- 覆盖结论四类：收益率未达门槛、达门槛但回撤不足、达门槛且回撤达标、四项取 min 分别由不同项决定（`浮盈×收割比例÷单位净值` / `份额×单次上限` / `份额×(1−最低保留)` / `成熟可赎回份额`）
- 旧侧命中：**40**；新侧命中：**40**
- **差异：0**；建议份额**全部精确相等**（`compareTo == 0`），**未出现任何需要近似比较的末位差异**（近似比较次数 = 0）——两侧都使用 `MathContext.DECIMAL64` 且运算顺序一致。

### 测试 C：生产历史状态组合回放

- 回放来源：`market-indicator-snapshot-export.csv`（生产库 `market_indicator_snapshot join fund_product`，业务日期 **2026-07-04 ~ 2026-09-24**）
- 回放的 (基金, 交易日) 对数：**1581**（涉及 **33 只基金、64 个交易日**，非全笛卡尔积，即生产库真实存在的快照行）
- 旧侧命中数：**0**
- 新侧命中数：**0**
- **差异数：0**
- 按 `product_type` 分组命中数：旧侧 `{}`、新侧 `{}`（两侧均为空，因为命中数为 0）

回放并非空转——近失（near-miss）统计说明判定确实被逐条比较过：

| 统计项 | 数量 |
| --- | --- |
| 快照行数（`product_type` 分布） | ETF 1088 / ACTIVE 232 / INDEX 167 / INDEX_ENHANCED 94 |
| 空值分布 | `price_above_year_line` 空 117；`weekly_macd_state` 空 81；`volume_state` 空 674 |
| 满足「跌破年线 + 周线 MACD 绿柱扩大」两条件、仅差量能条件的行 | 96（全部为 ETF，量能均为 `NORMAL`/`LOW_STABLE`/空） |
| `volume_state = HIGH_DROP` 的行 | 仅 2 行，且均为 `price_above_year_line=true` + `RED_SHRINKING`，不构成命中 |
| ACTIVE 且满足「跌破年线 + 绿柱扩大」的行 | 0 |

结论：生产快照中不存在任何一种逻辑破坏止损的命中组合。这既解释了旧侧 0 命中，也证明**迁移不会凭空多出或漏掉 SELL**，与计划 §1.5 的生产实测（`discipline_advice` 8 行、无一条 SELL）互相印证。

---

## 3. 差异表

**差异总数：7 条，全部落在同一个单元格上。**

| # | 产品类型 | 年线位置 | 周线 MACD | 量能状态 | 持仓状态 | 持仓份额 | 旧侧 | 新侧 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | ETF | 跌破年线 | `GREEN_EXPANDING` | `HIGH_DROP` | `OPEN` | 0 | 卖出（0 份） | 不卖 |
| 2 | INDEX | 跌破年线 | `GREEN_EXPANDING` | `HIGH_DROP` | `OPEN` | 0 | 卖出（0 份） | 不卖 |
| 3 | INDEX_ENHANCED | 跌破年线 | `GREEN_EXPANDING` | `HIGH_DROP` | `OPEN` | 0 | 卖出（0 份） | 不卖 |
| 4 | ACTIVE | 跌破年线 | `GREEN_EXPANDING` | `HIGH_DROP` | `OPEN` | 0 | 卖出（0 份） | 不卖 |
| 5 | ACTIVE | 跌破年线 | `GREEN_EXPANDING` | `NORMAL` | `OPEN` | 0 | 卖出（0 份） | 不卖 |
| 6 | ACTIVE | 跌破年线 | `GREEN_EXPANDING` | `LOW_STABLE` | `OPEN` | 0 | 卖出（0 份） | 不卖 |
| 7 | ACTIVE | 跌破年线 | `GREEN_EXPANDING` | 无数据 | `OPEN` | 0 | 卖出（0 份） | 不卖 |

**归因（不接受差异被掩盖，也不为此放宽断言）：**

- 唯一来源是「持仓状态 `OPEN` 且持仓份额为 0」这一单元格：旧 `AdvicePolicy` 命中逻辑破坏止损时**不做「持仓份额为正」的前置校验**，直接返回 `Result(SELL, holdingShares=0, "SHARE", "LOGIC_BROKEN", ...)`，即「建议卖出 0 份」；新 `AlertSuggestionService.logicBroken` 增加了 `positive(holdingShares)` 前置校验，因此不建议卖出，也就不发送邮件。这是新实现的一处**防御性收紧**，不是漏判。
- 该单元格在会计口径下**不可达**：`accounting` 的 `Position.reconcile` 状态判定为
  `netShares.signum() > 0 ? OPEN : CLEARED`，无 CONFIRMED 账本时为 `EMPTY`；
  因此 `OPEN` 必然对应正份额，`EMPTY`/`CLEARED` 必然是零份额。生产链路不可能产出 `OPEN + 份额 0` 的持仓事实。
- 差异条数 7 = `ACTIVE` 的 4 个量能取值（`HIGH_DROP` / `NORMAL` / `LOW_STABLE` / 无数据，主动型豁免量能条件）+ 其余 3 个产品类型的 `HIGH_DROP`（各 1 条）。

**其余 1433 个组合（含全部会计口径可达组合）差异为 0。** 测试 A 因此把断言拆成两桶，并且**没有放宽**：

- 可达组合（`OPEN ⇒ 份额>0`、`EMPTY`/`CLEARED` ⇒ 份额=0）差异**必须为空**；
- 不可达组合的差异**被固定为恰好 7 条**，且逐条断言必须落在 `OPEN + 份额=0` 且方向必须是「旧卖出 / 新不卖」——任何新增/其它差异都会让测试失败。

测试 B、测试 C 的差异数均为 **0**（断言为「必须为空」，无任何豁免）。

---

## 4. 验证边界

1. **本回放验证的是判定规则映射与 ACTIVE/null 豁免在真实历史状态组合上的一致性。** 原始净值/量能序列 → 状态枚举（`price_above_year_line`、`weekly_macd_state`、`volume_state`）的推导属于 A 段指标计算与既有 marketdata 测试的范围，本脚本不重复验证，也不对其正确性背书。
2. **回撤止盈缺少历史持仓快照，故以全量参数网格穷举替代历史序列回放。** 生产库中 3 个 `EFFECTIVE` 策略的 `take_profit_phase` 全为 `ACCUMULATING`，历史上没有止盈触发事件需要保真（计划 §1.5、§7）；因此测试 B 用 8640 组参数/状态网格覆盖了「累计 → 就位 → 新高抬升 → 回撤触发 → 冷静期」的全部阶段与四档预设，而不是逐日回放。
3. **测试 A 的 7 条差异只出现在会计口径不可达的组合上**（见 §3），是可解释且已接受的差异；可达组合上差异为 0。
4. **测试 C 两侧命中数均为 0**，其价值在于证明「迁移不会凭空多出或漏掉 SELL」，而不是复现历史行为；判定本身的区分能力由 96 条「仅差量能条件」的两条件行与测试 A 的 1440 组合共同覆盖。
5. **本比对不覆盖**：提醒状态机落库（`SuggestionState` 的持久化与幂等键）、邮件正文渲染与发送失败重试、以及 A 段条件引擎的窗口可配性——这些属于 A/B 段各自的既有测试范围。

---

## 5. 结论

判定口径迁移**等价**：在会计口径可达的状态空间（测试 A）、回撤止盈的全部参数与状态组合（测试 B）、以及 1581 组真实历史状态组合（测试 C）上，新旧两侧的「是否卖出」逐一相等；回撤止盈的建议卖出份额全部精确相等。唯一差异为 7 条、全部位于会计口径不可达的 `OPEN + 份额=0` 单元格，成因是新实现增加了一处「份额为正」的防御性前置校验，已逐条列出并归因。差异不为空这一事实已如实记录，未通过放宽断言或删除组合的方式掩盖。
