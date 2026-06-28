# 初始持仓录入：新建基金录现有金额，同步确认建仓

新建基金时支持录入已有持仓（`FundCreateRequest.existingAmount`）。有值即触发建仓：
状态流转对齐 BUILD 信号确认（`FundStatus → HOLDING`、写 INCREASE 交易），
但交易**同步确认**——用最近一期已公布净值反算 shares、置 CONFIRMED，不等 NavConfirmJob。
`openedAt` 用户可填（大致建仓时点，影响移动止盈持仓期高点起算），不填用 now；须 ≤ 今天。
无净值历史可反算时抛 `NAV_HISTORY_EMPTY` 不让建。

> **修订**：初版 `openedAt` 固定用 now（理由"现有金额不携带真实建仓时间"）。
> 后补充 `openedAt` 可填——用户记得大致建仓时点，填了让持仓期高点从该时点起算。
> 金额仍是**当前市值口径**（"现在有多少钱"），净值用最近一期反算，openedAt 只标时间不影响净值反算。

## Considered Options

- **A. 状态流转对齐 build + 同步用最近净值确认（已采纳）**：existingAmount 有值 →
  `FundStatus→HOLDING` + `openedAt=now` + INCREASE 交易同步确认（最近净值反算 shares）。
- **B. 完全对齐 build（写 PENDING，当晚 job 用当日净值确认）**：复用 handleBuild 全路径零特例。
  但语义错位——把"历史持仓盘点"当"今天新买入"，用户当下看不到持仓确认，要等晚上 job。
- **C. 用昨日净值 + TRANSFER_IN + 同步确认**：不刻意对齐 build。但建仓是首笔买入非转入，
  INCREASE 语义更准；状态流转不统一会增加心智负担。

## Consequences

选 A 的核心理由：**状态机对齐 build，确认时机尊重"现有金额是历史持仓"**。

1. **状态流转对齐 handleBuild**——`FundStatus→HOLDING`、INCREASE 来源全一致。
   建仓动作的交易来源统一，不引入"建仓还能用别的来源"的心智特例。
2. **同步确认而非异步**——现有金额是用户**已经持有**的仓位盘点（当前市值口径，可能上周/上月就买了），
   不是今天新买入。用最近一期已公布净值当下反算 shares、置 CONFIRMED，用户建完基金立刻看到持仓。
   走 NavConfirmJob（B）要等当晚、且新基金当日净值未必公布，可能长期 PENDING。
3. **openedAt 用户可填**——金额是当前市值口径（净值用最近一期反算），但用户记得大致建仓时点。
   openedAt 让用户填，使移动止盈的"持仓期高点"从用户记得的时点起算，而非强制从今天。
   不填用 now（用户不记得或不在意）。openedAt 只影响高点起算，不影响净值反算——金额是"现在有多少钱"，
   净值必用最近一期；若误用历史净值反算，份额与当前市值对不上。须 ≤ 今天（防手滑填未来）。

## 代价

**事务回滚的孤儿净值**——`create` 加 @Transactional，openWithExistingPosition 抛错（无净值）时
外层回滚基金 save，但 `fetchOneFund`（REQUIRES_NEW 独立事务）已提交的净值历史成孤儿。
可接受：净值是行情数据非业务数据，下次 refresh 复用；且回滚基金 save 是对的
（用户要建带仓位基金，取不到净值就该整体失败）。

**openedAt 仍是近似值**——用户填的是"大致建仓时点"（记得是某月某周），未必精确到交易日。
持仓期高点从该近似时点起算，仍可能有几天偏差，但远好于强制从今天起算。
openedAt 须 ≤ 今天（防手滑填未来，抛 `OPENED_AT_IN_FUTURE`）。

## 与手动交易的边界

初始持仓录入 ≠ 手动交易。手动交易是已建仓后的资金动作（NavConfirmJob 异步确认）；
初始持仓录入是建仓本身（同步确认）。两者都复用 FundTransactionEntity、signalLog=null，
但触发点（新建 vs 详情页）、确认时机（同步 vs 异步）、语义（建仓 vs 资金动作）不同。
详见 CONTEXT.md「初始持仓录入」「手动交易」。
