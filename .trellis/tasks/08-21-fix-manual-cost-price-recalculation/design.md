# 成本基准重置技术设计

## 边界

- 复用现有 `fund_transaction` 账目流水，新增来源 `COST_BASIS_RESET`，不新增表、字段或依赖。
- `accounting_position.cost_per_share` 继续作为当前成本投影；交易与重置记录组成可确定顺序的成本事实。
- 历史 FIFO lot 继续只负责已实现盈亏和赎回费，不参与当前成本修正。

## 流水契约

成本修正通过现有 PortfolioCorrection 写锁和事务边界执行，写入一条创建即确认的流水：

- `source = COST_BASIS_RESET`
- `status = CONFIRMED`
- `tradeDate = confirmTime = 修正发生时间`
- `shares = 修正时已确认净份额快照`
- `amount = shares × 修正后的 costPerShare`，按现有金额精度保存
- `nav / fee / feeRate = null`

成本价由 `amount / shares` 还原并按 8 位小数规范化。该来源的份额方向为 0，既不是买入、卖出，
也不是份额调整；普通手动交易 API 明确拒绝该来源。记录发布与现有即时确认调整一致的 Created 事件，
不伪造待确认或净值确认流程。

## 写入流程

```text
FundsPage 编辑成本价
  -> FundService.update
  -> PortfolioCostCorrectionApi
  -> 锁定并校验 PortfolioFund / OPEN Position
  -> 汇总当前 CONFIRMED 净份额
  -> 保存 COST_BASIS_RESET 流水
  -> 修正 Position.costPerShare
  -> 同一事务提交
```

任一写入失败时事务整体回滚，不留下只有流水或只有 Position 的半状态。保存成功后同时失效基金、
组合收益和交易流水查询。

## 重放规则

账本按 `effectiveTradeDate` 升序、同刻按流水 ID 升序排列。只有存在成本基准重置的持仓启用重置重放；
没有重置记录的存量持仓保留现有增量加权行为，避免上线前手工值被历史重算覆盖。

从最近一次有效重置开始：

1. 先用重置前流水计算该时点有效份额，但忽略其历史买入成本。
2. 用重置记录的 `amount / shares` 取得用户输入的每份成本，并把当时全部有效份额视为新成本基准。
3. 后续买入按现有投入金额加权；卖出和调减只减少份额，不改变每份成本。
4. 重置后的调增仍作为零成本份额；下一次买入时沿用既有零成本稀释语义。
5. 清仓后再入场由首笔新买入自然建立新成本。

因此，业务时间早于重置但稍后才确认的 PENDING 买入会排在重置之前，不会再次覆盖修正结果。

## 查询与展示

- 交易查询继续返回现有 `amount`、`shares` 和 `source`；前端仅对 `COST_BASIS_RESET` 计算并展示
  `amount / shares` 为“成本价”。
- 来源标签显示“成本修正”，状态显示“已确认”；行内不出现编辑、确认或撤销动作。
- “手动录入交易”来源选项不包含 `COST_BASIS_RESET`。

## 兼容性

- Accounting、legacy Fund 枚举和所有份额方向 switch 必须同步识别新来源为零方向，避免旧查询链反序列化失败。
- 一次性 Accounting rebuild 对该来源跳过净值和 lot 处理，不能把它当未知交易中断。
- 上线前的手工修正不会生成伪造历史记录；当前 Position 值继续保留，用户再次保存后才建立重置点。

## 风险与回滚

- 新来源写入现有表后，旧应用版本无法解析它。回滚旧镜像前需软删除新来源行：

```sql
UPDATE fund_transaction
SET deleted_date = now()
WHERE source = 'COST_BASIS_RESET' AND deleted_date IS NULL;
```

- 软删除不改当前 Position 成本，记录可在恢复新版本后将 `deleted_date` 置空找回。
- 回滚代码后不再具备重置顺序语义，后续买入恢复旧加权行为；这是功能回滚的预期影响。
- 不新增 ADR：本次更新既有 ADR-0013 的成本维护规则，不形成新的独立架构决策。
