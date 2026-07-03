# 实施规划：行情工作台

> 任务：将平台重点转向实时行情工作台，定投止盈降级为辅助模块
> 前置：PRD + Design 已完成并审阅

## 实施阶段

按依赖顺序分 6 个阶段，每阶段独立可验证。

---

## 阶段 1: 后端行情数据源扩展

**目标**：新增指数实时行情、板块涨跌、资金流向三类数据接口

### 1.1 新增 Feign 客户端

- [ ] `EastmoneyIndexRealtimeClient` — 指数实时行情（push2.eastmoney.com，secid 复用 SecidFormat）
- [ ] `EastmoneySectorClient` — 行业板块涨跌排行
- [ ] `EastmoneyMoneyFlowClient` — 北向/主力资金流向
- 复用现有 `RateLimiter` 共享限流（2 req/s）

### 1.2 解析器

- [ ] `EastmoneyJsParser.parseIndexRealtime()` — 指数实时数据（名称、点位、涨跌额、涨跌幅、时间）
- [ ] `EastmoneyJsParser.parseSectorList()` — 板块涨跌（名称、代码、涨跌幅、领涨股）
- [ ] `EastmoneyJsParser.parseMoneyFlow()` — 资金流向（北向、主力、超大单、大单、中单、小单）

### 1.3 单元测试

- [ ] 每个解析器配 MockWebServer 测试（canned 响应 + 边界情况）
- [ ] 复用现有测试模式（见 `EastmoneyClientIntegrationTest.java`）

**验证**：`mvn test -Dtest="Eastmoney*Test"`

---

## 阶段 2: 后端行情缓存层

**目标**：内存缓存 + 定时刷新，解决高频刷新 vs 2 req/s 限流矛盾

### 2.1 缓存服务

- [ ] `MarketRealtimeCache` — ConcurrentHashMap 缓存（指数/板块/资金/基金估值）
- [ ] `MarketRealtimeRefreshJob` — `@Scheduled` 定时刷新任务
  - 交易时段（9:30-11:30, 13:00-15:00）每 30s 刷新一次
  - 非交易时段休眠
- [ ] 交易日判断复用现有 `TradingCalendar`

### 2.2 REST 接口

- [ ] `GET /api/market/indices/realtime` — 用户关注指数的实时行情（读 user_config）
- [ ] `GET /api/market/funds/estimates?codes=xxx,yyy` — 批量基金估值（读缓存）
- [ ] `GET /api/market/sectors` — 行业板块涨跌排行（读缓存）
- [ ] `GET /api/market/money-flow` — 资金流向（读缓存）
- [ ] `GET /api/market/funds/{fundId}/kline?period=daily|weekly|monthly` — K线数据（按需拉取，不缓存）

### 2.3 用户配置扩展

- [ ] `user_config` 表新增 `watched_indices` 字段（JSON 数组或单独关联表）
- [ ] Flyway migration
- [ ] `UserConfigController` 增加「关注指数」CRUD

**验证**：`mvn test` + 启动后访问接口确认返回数据

---

## 阶段 3: 前端导航重组

**目标**：侧边栏改为「行情/策略/系统」三组，首页切换

### 3.1 路由调整

- [ ] `App.jsx`：`/` 指向 `MarketDashboard`（新组件）
- [ ] `/dashboard`（旧首页保留路由但移出默认）
- [ ] `/funds/:fundId` 保持不变

### 3.2 Shell 导航重组

- [ ] `Shell.jsx`：`NAV_GROUPS` 改为「行情/策略/系统」
- [ ] 「行情」组：行情工作台（`/`）
- [ ] 「策略」组：交易信号、操作确认、我的基金
- [ ] 「系统」组：保持不变
- [ ] `PAGE_META` 新增 `/` → 「行情工作台 / 实时行情与市场动态」
- [ ] 移动端底部导航同步调整

**验证**：手动检查导航跳转、首页进入行情工作台

---

## 阶段 4: 行情工作台前端

**目标**：构建行情工作台主页（指数条 + 基金列表 + 板块 + 资金流向）

### 4.1 API Hooks

- [ ] `useRealtimeIndices()` — 指数实时行情（5s 轮询）
- [ ] `useFundEstimates(codes)` — 基金估值（10s 轮询）
- [ ] `useSectorPerformance()` — 板块涨跌（30s 轮询）
- [ ] `useMoneyFlow()` — 资金流向（30s 轮询）
- [ ] 复用 `@tanstack/react-query` 的 `refetchInterval`

### 4.2 组件实现

- [ ] `IndexTicker.jsx` — 指数条（横向滚动卡片 + 编辑按钮）
- [ ] `FundWatchlist.jsx` — 自选基金行情列表（可排序表格）
- [ ] `SectorPerformance.jsx` — 行业板块涨跌（进度条样式）
- [ ] `MoneyFlow.jsx` — 资金流向（4行关键数据）
- [ ] `MarketDashboard.jsx` — 组装以上组件

### 4.3 样式

- [ ] `styles.css` 新增行情组件样式
- [ ] 涨跌色 + 箭头图标（避免仅靠颜色）
- [ ] 骨架屏加载状态
- [ ] `prefers-reduced-motion` 支持

**验证**：启动前端 + 后端，确认数据轮询、刷新、排序功能正常

---

## 阶段 5: K线图集成

**目标**：基金详情页加入 K线/走势图

### 5.1 安装图表库

- [ ] `npm install lightweight-charts`

### 5.2 图表组件

- [ ] `KlineChart.jsx` — 蜡烛图（OHLC + 成交量，日/周/月切换）
- [ ] `NavChart.jsx` — 净值走势图（折线 + 基准对比 + 时间范围切换）
- [ ] 根据 `fundSubType` 自动选择图表类型

### 5.3 集成到基金详情

- [ ] `FundMarketTab.jsx` 增加图表区域
- [ ] `useFundKline(fundId, period)` — 拉取 K 线数据
- [ ] `useFundNavHistory(fundId, range)` — 拉取净值历史

**验证**：ETF 显示K线+成交量，主动基金显示净值走势

---

## 阶段 6: 收尾

- [ ] 删除/降级旧 DashboardPage 的策略 KPI（或保留为 `/dashboard` 备用入口）
- [ ] 全量回归测试：现有功能（信号、确认、基金管理、配置、监控）不受影响
- [ ] 响应式测试：375px / 768px / 1024px / 1440px
- [ ] 无障碍检查：reduced-motion、键盘导航、色对比度
- [ ] Spec 更新（Phase 3.3）

---

## 风险与回滚点

| 风险 | 应对 |
|------|------|
| 东方财富 API 限流被封 | 缓存层兜底，降级返回缓存数据 + 延迟提示 |
| 板块/资金流向接口结构不稳定 | 解析失败时返回空数组 + 优雅降级 |
| Lightweight Charts 与 React 集成问题 | 如遇阻，回退到 ECharts（设计已兼容） |
| 旧 DashboardPage 删除影响 | 阶段 6 才删，前期保留 `/dashboard` 路由 |

## 验证命令

```bash
# 后端
cd backend && mvn test
cd backend && mvn spring-boot:run

# 前端
cd frontend && npm install
cd frontend && npm run dev
```