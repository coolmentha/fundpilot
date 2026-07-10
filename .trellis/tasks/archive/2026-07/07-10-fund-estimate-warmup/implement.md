# 实施计划：盘后重启基金估值异步预热

1. 修改 `DailyChangeResolverTest`，先将无估值降级期望改为未知值并确认测试失败。
2. 修改 `MarketRealtimeCacheTest`，覆盖同步启动监听不阻塞、异步启动监听会刷新全部基金估值。
3. 在 `MarketRealtimeCache` 增加 `@Async` 的基金估值启动预热入口，复用现有刷新逻辑。
4. 修改 `DailyChangeResolver`，移除 T-1 对 T-2 冒充今日值的降级。
5. 组合中任一持仓今日收益未知时，让 `dailyPnlTotal` 返回 null，前端显示 `-` 而不是假 0 或部分合计。
6. 更新 `CONTEXT.md`、ADR-0008 和 `.trellis/spec/backend/market-realtime-cache.md`，明确盘后重启与缓存缺失契约。
7. 运行聚焦测试：`DailyChangeResolverTest`、`MarketRealtimeCacheTest`、`FundPnlServiceDateTest`、`PortfolioSummaryCalculatorTest`、`FundPnlServiceTest`。
8. 运行后端编译/完整验证、前端生产构建和 `git diff --check`。
9. 提交推送后持续监控分支 CI；全绿后归档任务、合并 `main`、打下一个 patch tag 并监控部署 Smoke test。

## 风险点

- `@Async` 必须作用在 Spring 事件监听代理调用上，测试需覆盖事件方法调用路径和启动不阻塞契约。
- `null` 今日涨跌必须让全仓今日合计也变为未知，不能按 0 忽略后展示部分合计。
- 不得把外部估值请求移入 API GET 请求链路。
