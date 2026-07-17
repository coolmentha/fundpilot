# 初始持仓录入：新建基金录持有份额，同步确认建仓

新建基金时支持录入已有持仓（`FundCreateRequest.initialHoldingShares`）。有值即触发建仓：
状态流转对齐 BUILD 信号确认（`FundStatus → HOLDING`、写 INCREASE 交易），
但交易**同步确认**——直接保存用户份额，用最近一期已公布净值计算核算金额并置 CONFIRMED，不等 NavConfirmJob。
`openedAt` 用户可填（大致建仓时点，影响移动止盈持仓期高点起算），不填用 now；须 ≤ 今天。
无净值历史可核算时抛 `NAV_HISTORY_EMPTY` 不让建。

> **修订**：初版按当前市值除最近净值反推份额，行情刷新后流水金额与当前市值立即不同，容易被误解为本金损失。
> 现改为直接录入用户实际持有份额；最近净值只用于生成交易核算金额，不改变份额。

## Considered Options

- **A. 状态流转对齐 build + 同步用最近净值确认（已采纳）**：initialHoldingShares 有值 →
  `FundStatus→HOLDING` + INCREASE 交易同步确认（直接保存 shares，最近净值计算 amount）。
- **B. 完全对齐 build（写 PENDING，当晚 job 用当日净值确认）**：复用 handleBuild 全路径零特例。
  但语义错位——把"历史持仓盘点"当"今天新买入"，用户当下看不到持仓确认，要等晚上 job。
- **C. 用昨日净值 + TRANSFER_IN + 同步确认**：不刻意对齐 build。但建仓是首笔买入非转入，
  INCREASE 语义更准；状态流转不统一会增加心智负担。

## Consequences

选 A 的核心理由：**状态机对齐 build，事实份额以用户账面数据为准**。

1. **状态流转对齐 handleBuild**——`FundStatus→HOLDING`、INCREASE 来源全一致。
   建仓动作的交易来源统一，不引入"建仓还能用别的来源"的心智特例。
2. **同步确认而非异步**——录入的是用户**已经持有**的仓位盘点，不是今天新买入。直接保存实际份额并置 CONFIRMED，用户建完基金立刻看到持仓。
   走 NavConfirmJob（B）要等当晚、且新基金当日净值未必公布，可能长期 PENDING。
3. **openedAt 用户可填**——用户记得大致建仓时点时可补充该时间。
   openedAt 让用户填，使移动止盈的"持仓期高点"从用户记得的时点起算，而非强制从今天。
   不填用 now（用户不记得或不在意）。openedAt 只影响高点起算，不影响录入份额或交易核算净值。须 ≤ 今天（防手滑填未来）。

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
