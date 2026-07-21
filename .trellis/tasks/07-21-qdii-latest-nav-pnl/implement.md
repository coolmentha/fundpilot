# QDII 最新净值收益实施清单

1. 读取 backend/frontend Trellis spec 与相关收益测试。
2. 先补 QDII 日期滞后且估值同时存在的服务测试，确认当前行为失败。
3. 在 `FundPnlService` 中让 QDII 优先使用最新两期确认净值；A 股路径不变。
4. 调整基金列表、观察列表和详情页的 QDII 收益依据日期文案，复用现有字段。
5. 运行聚焦后端测试、前端测试/lint/build，并检查工作区差异。

## 验证

- `cd backend; ./mvnw '-Dtest=FundPnlServiceDateTest,DailyChangeResolverTest' test`
- `cd frontend; npm test`
- `cd frontend; npm run lint`
- `cd frontend; npm run build`

## 风险文件

- `backend/src/main/java/com/fundpilot/backend/fund/service/FundPnlService.java`
- `frontend/src/pages/FundsPage.jsx`
- `frontend/src/components/FundWatchlist.jsx`
- `frontend/src/pages/FundDetailPage.jsx`
