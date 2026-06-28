# 持仓成本从派生总额改为存储单价

原模型：持仓成本 = Σ CONFIRMED 交易 amount（总金额），实时穿透交易表派生，不存字段。现在替换为 `FundEntity.costPerShare`（持仓成本价/成本单价），维护而非派生——创建时手填或默认为 T-1 净值，后续 CONFIRMED 买入交易同步加权更新。

## Considered Options

- **A. 存储成本单价 + 加权维护（已采纳）**：`FundEntity.costPerShare` 单一字段。建仓时 `shares = initialMarketValue / T-1净值`，costPerShare 用户可填(不填用 T-1净值)。后续 INCREASE/TRANSFER_IN/INVEST 交易 CONFIRMED 时同一事务内加权：`新 = (旧×旧份额 + amount) / (旧+新)`。卖出不触发重算。总盈亏 = `shares × (latestNav - costPerShare)`。
- **B. 保持派生总额 + 另加参考单价**：保留 `getCost()` 总额派生逻辑，额外在 FundEntity 加 costPerShare 只作展示参考。两套成本口径并存，同类指标含义分裂。
- **C. 拆除交易总额派生、完全依赖存储**：但存储总额而非单价。份额变动后成本总额需要随份额联动维护（卖出时减 cost 分支），复杂度高于单价方案（单价天然不受卖出影响）。

## Consequences

1. **总盈亏公式简化**：从 `shares × nav - Σ amounts` 变为 `shares × (nav - costPerShare)`，不再穿透交易表。
2. **FundPositionService.getCost() 删除**，唯一消费方 FundPnlService 改为直读 FundEntity.costPerShare。
3. **建仓入口变更**：`existingAmount` 重命名为 `initialMarketValue`，新增可选 `costPerShare`（>0 校验）。
4. **加权维护在交易 confirm 方法内同一事务**执行，避免不一致窗口。
5. **成本单价不受卖出影响**——卖出只减份额，costPerShare 不变。天然避免"卖出后成本基准该不该减"的歧义。
6. **清仓再入场时旧 costPerShare 被覆盖**（新交易 oldShares=0 → 新单价 = amount/shares），无需特殊处理。
