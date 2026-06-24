# 前高与持有期高点不在 FundEntity 上落字段，改为实时派生

`FundEntity` 上原设计的 `peakNav`（基金历史前高，加仓档位基准）和 `holdingPeriodPeakNav`（持有期高点，移动止盈基准）两个字段已删除，改为通过
`max(fund_nav_history.accumulated_nav)` 实时派生（持有期高点附加 `WHERE nav_date >= fund.openedAt` 条件）。

## Considered Options

- **A. 字段落库 + `NavConfirmJob` 每晚增量刷新（原设计）**：和"事实账目下单写已知侧、job 回填另一侧"的写入模式对称，符合直觉。
- **B. 不落字段，每次实时派生（已采纳）**：和"持仓金额永远实时算"的硬性原则一致。

## Consequences

选 B 的核心理由：派生值落库存在三种失真风险，且全部无法靠"再多跑一次 job"消除。

1. **净值修正**：基金公司修正前日累计净值（分红除权、错账修正），`fund_nav_history` 改一行即可——但已落库的 `peakNav`
   不会跟着修正，永久不一致。
2. **补录历史净值**：补录的净值若大于现 `peakNav`，字段要等下次 job 才刷新；中间窗口的信号生成全用旧值。
3. **Job 异常**：`NavConfirmJob` 漏跑一天 + 同期人工补净值，字段直接和事实表脱钩。

实时派生的查询有 `(fund_id, nav_date)` 索引，单值 `max()` 毫秒级返回。每日 14:50 批量跑所有 EFFECTIVE
基金（即便上百只）也只是同等数量的索引查询，非瓶颈。

权衡的小代价：以后做"前高曲线图"等读密集展示场景需要加缓存；本期没有这个需求，不预先优化。
