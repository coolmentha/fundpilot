# 实施计划

- [x] 扩展东方财富指数实时请求字段，新增市场宽度快照与解析逻辑。
- [x] 调整 `MarketRealtimeCache`，一次请求同时更新自选指数和固定沪深京市场宽度缓存。
- [x] 增加市场宽度 View 和只读 Controller 接口。
- [x] 增加前端查询 Hook，在 `PortfolioOverview` 增加左红右绿的市场宽度进度条卡片并调整响应式布局。
- [x] 补充解析器与缓存单元测试。
- [x] 更新 `CONTEXT.md` 和 `.trellis/spec/backend/market-realtime-cache.md` 的市场宽度契约。
- [x] 运行新增相关后端测试、前端生产构建和 Playwright 桌面/手机布局检查。
- [ ] 提交、推送后运行完整 CI（含 PostgreSQL service container）并持续检查结果。

## Rollback Points

- 后端响应为独立新接口，回滚时可整体删除，不影响现有指数和组合摘要接口。
- 前端新增卡片与 Hook 可独立回滚，不改变组合摘要数据契约。

## Validation Commands

```powershell
cd backend
.\mvnw.cmd test

cd ..\frontend
npm run build
```

## Validation Results

- `EastmoneyJsParserRealtimeTest,MarketRealtimeCacheTest`:15 tests passed.
- `npm run build`:passed；保留项目已有的 bundle size warning。
- Playwright:1440x1000 与 390x844 均无横向溢出；红段实测 69.2%，与 `3814 / (3814 + 1701)` 一致。
- `mvn test` 全量尝试:测试编译通过，运行到数据库集成测试时因本机 `localhost:5432` 未启动而失败；Docker Desktop 同样未运行。CI 的 PostgreSQL service container 可提供该环境，推送后需以 CI 结果为准。
