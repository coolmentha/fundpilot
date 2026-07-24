# Design: 定投预算总览与仓位提醒

## Data Model

- 用数据库迁移删除 `user_config.total_capital` 及其约束；将 `fund.max_position_ratio` 重命名为 `position_warning_ratio`
  ，保留用户已设置的每基金阈值并移除旧的 30% 硬上限。
- 在 `user_config` 增加可空 `monthly_dca_budget NUMERIC(19, 8)`，仅允许正数；`null` 表示不比较预算。
- 在 `fund` 增加 `position_warning_enabled BOOLEAN NOT NULL DEFAULT true` 与
  `position_warning_ratio NUMERIC(8, 6) NOT NULL DEFAULT 0.30`，比例允许 `(0, 1]`。
- 保留用户已有关注指数配置；迁移不推断预算，也不改写交易历史。

## Backend Contract

- `UserConfigView` 返回 `monthlyDcaBudget`，用户配置更新接口可覆盖或清空该字段。
- 新的只读定投预算摘要接口返回：预算、已定投、未来定投、预计定投和剩余/超额。摘要计算服务成为唯一的日期与金额口径。
- 统计按 `ChinaTradingDate` 的上海自然月边界执行。已定投查询只含 `source=INVEST` 且状态不是 `CANCELLED` 的交易。
- 未来计划复用 DCA 的交易日语义，按实际执行日检查，排除已有任意状态交易的计划日期。月计划跨月顺延由实际日期归属。
- 删除 `PositionLimitService` 在确认、建仓和更新路径中的调用；删除 `CAPITAL_POOL_NOT_CONFIGURED` 与
  `POSITION_LIMIT_EXCEEDED` 的业务含义。
- 当前仓位占比使用已确认事实持仓与当前持仓市值，不将 PENDING 或未来计划纳入；任一已持仓基金当前市值未知时，整组占比返回空而非按可用子集计算。

## Frontend Contract

- 设置页用受控金额输入保存 `monthlyDcaBudget`，允许清空；不再渲染外部入金控件。
- 基金页在列表前增加紧凑的本月定投总览：预算未设置时显示金额和设置入口；预算设置时使用带文字标签的分段进度条和超额说明。
- 基金表格展示当前仓位占比与提醒线，超线且启用时使用明确状态文案；编辑弹窗提供开关与 1% 至 100% 的输入。
- 复用现有 Ant Design 组件与深色数据密集界面，不添加新依赖。

## Compatibility and Rollback

- 该迁移会移除旧列及约束，回滚需要恢复列和约束的独立迁移，不能依赖 Flyway 回退。
- 已配置的 `total_capital` 不迁移为新预算；新预算应由用户主动设置。旧 `max_position_ratio` 迁移为同值的
  `position_warning_ratio`，以保留用户的提醒偏好。
- 发布前验证历史 PENDING 买入/转换可在净值存在时完成确认，避免旧错误码继续出现。
