# 技术设计

## 边界

复用 `PortfolioReturnService` 的当前组合计算结果，新增一张按业务日期唯一的组合快照表、一个幂等写入服务和一个查询区间接口。前端只消费区间接口，不在浏览器重算收益。

## 数据流

1. 净值确认及早间补拉完成后，定时任务调用快照服务。
2. 快照服务读取当前组合收益和每只基金的估值完整性，按净值业务日期 upsert。
3. 区间查询按日期范围读取快照，使用首个快照作为基线，计算区间资金流、收益和最大回撤。
4. QDII 晚到时再次执行 upsert，只更新同一业务日期，不新增点。

## 数据契约

- `portfolio_return_snapshot`: `business_date` 唯一；金额字段使用项目现有 decimal 精度；保存 `valuation_complete`、
  `missing_fund_count`、`missing_fund_codes`、`captured_at`。
- API 返回 `dataStartDate`、`latestDate`、`incomplete`、`missingFundCodes`、曲线点和区间摘要。
- 没有基线快照时返回空曲线和数据不足状态，不伪造零收益。

## 时间与一致性

- 任务使用 `Asia/Shanghai`，业务日期使用现有交易日历/净值日期工具。
- 写入采用数据库唯一约束 + upsert，任务重跑安全。
- 估值不完整时仍保存可计算金额；补拉后覆盖同日记录。

## 迁移与回滚

- 新增 `V24` Flyway 迁移，不改 v0.9 表结构。
- 回滚时停用快照任务和趋势路由，保留表数据；v0.9 `/api/portfolio/returns` 不受影响。
- 迁移失败应阻止应用启动，修复迁移后重试，不执行破坏性删除。

## 取舍

- 首版不做历史回溯、XIRR、年化和基准对比。
- 缺失基金代码数量较少时直接存字符串，避免为一项提示引入关联表；查询返回时拆分为数组。
