# 行情工作台数据展示设计

## Boundaries

保持现有数据流：外部行情客户端负责原始字段，`MarketRealtimeCache` 负责完整快照和 Redis 写穿，application query 只读缓存并结合交易日历形成状态，Controller 返回 View，前端通过现有 React Query hooks 轮询。

不新增数据源、状态服务或前端状态库。原 `MoneyFlow.jsx` 复用为行业表现表，`SectorPerformance.jsx` 负责取数和排序模式，避免留下重复请求与未使用组件。

## Backend Contracts

### Market breadth

- 东方财富指数请求增加 `f106`。
- `MarketBreadthSnapshot`、gateway record 和 `MarketBreadthView` 增加 `flatCount`。
- 上证、深证、北证三个固定市场的 `f104/f105/f106` 必须全部是非负整数，才与同花顺涨跌停数据组合发布。
- 旧 Redis JSON 缺少 `flatCount` 时反序列化为兼容空值，但不恢复为可发布快照。

### Complete sector range

- `clist` 的 `pz` 从 20 调整为 100，覆盖当前完整行业范围；仍只发起一次请求并复用现有字段。
- 后端保持返回原始行业快照，排序由前端按用户选择完成。

### Snapshot freshness

- `MarketRealtimeCache` 保存 `indicesUpdatedAt`、`breadthUpdatedAt`、`sectorsUpdatedAt`。
- 仅对应数据族成功替换缓存时更新其时间；空响应或异常保留旧数据和旧时间。
- Redis `Snapshot` 持久化三个时间字段；旧 JSON 缺字段时按 `null` 兼容。
- 工作台 `updatedAt` 仅在三类时间都存在时返回三者最旧值，否则返回 `null`。

### Market status

- 新增 `GET /api/market/status`，只读缓存时间和现有交易日历。
- 状态值：`PRE_OPEN`、`TRADING`、`LUNCH_BREAK`、`CLOSED`、`NON_TRADING_DAY`。
- 时间边界按 `Asia/Shanghai`：09:30 前盘前；09:30-11:30、13:00-15:00 交易中；11:30-13:00 午间休市；15:00 后已收盘。非交易日优先返回 `NON_TRADING_DAY`。

## Frontend Composition

- `MarketDashboardPage` 顶部标题区展示市场状态和 `updatedAt`，移除重复状态条，将持仓数据派生为最大贡献/拖累。
- `PortfolioOverview` 增加平盘家数，宽度条分母仍只使用上涨加下跌，保持现有视觉契约。
- `IndexTicker` 有有效 `turnover` 时展示成交额，否则展示 `changeAmount`。
- `querySafety` 透传 `returnRate`、`valuationNav`、`valuationFirstSeenAt`。
- `FundWatchlist` 移除仓位及侧栏，改为需求字段并默认按 `dailyPnl` 降序。
- `SectorPerformance` 使用分段控件维护本地排序模式；`MoneyFlow` 渲染统一行业表并计算 `mainforceNet / turnover`。

## Compatibility And Rollback

- 所有新增 JSON 字段对旧消费者透明；无 schema 变更。
- 旧 Redis 快照可读取，但没有完整宽度或刷新时间时返回空状态，下一次成功刷新后自动补齐。
- 回滚只需回退应用代码；Redis JSON 的额外字段会被旧代码忽略。

## Risks

- `pz=100` 依赖当前行业数量低于该上限；若未来超过 100，应改为分页拉取。当前不为未发生的规模新增分页复杂度。
- 状态时间取三类数据最旧值，显示会比某个单项的实际更新时间早，但不会掩盖局部陈旧。
- 移动端列宽需要通过横向滚动或响应式隐藏次要列保证文本不重叠。
