# 实施计划

1. 在 `MarketRealtimeCacheTest` 增加成功后空响应、异常、旧日期、失败后恢复成功的回归测试，并固定 `Clock`。
2. 在 `FundPnlServiceTest` 增加估值失败与当日净值已确认两个跨层场景。
3. 实现估值日期校验、旧缓存删除和失败状态集合。
4. 扩展 `Pnl`、`PortfolioSummary`、`FundView`、`PortfolioSummaryView`，修正估值未知时的当前市值/总盈亏计算。
5. 更新四个前端展示面，失败态优先显示明确文案。
6. 更新 `CONTEXT.md` 和 `.trellis/spec/backend/market-realtime-cache.md` 的估值失败契约。
7. 运行：
   - `backend\\mvnw.cmd -Dtest=MarketRealtimeCacheTest,FundPnlServiceTest test`
   - `backend\\mvnw.cmd verify`
   - `npm run build`（`frontend`）
   - 浏览器烟测总览、行情自选、基金列表和基金详情失败态。
