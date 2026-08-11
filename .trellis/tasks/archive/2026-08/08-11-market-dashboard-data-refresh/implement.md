# 行情工作台数据展示实施计划

## Implementation Checklist

- [x] 1. 补后端失败测试：`f106` 完整性、旧 Redis 兼容、三类更新时间和市场状态边界。
- [x] 2. 扩展市场宽度字段，行业请求改为完整范围，并在缓存成功路径记录/持久化更新时间。
- [x] 3. 通过现有交易日历增加只读市场状态查询与 View。
- [x] 4. 补前端字段透传和 hook，更新总览、指数、持仓贡献与持仓表。
- [x] 5. 复用 `MoneyFlow.jsx` 完成行业表现表和三种排序，移除工作台重复模块。
- [x] 6. 更新相关组件测试、缓存契约 spec 和过期注释。
- [x] 7. 运行后端定向测试、前端测试/lint/build、`git diff --check`。
- [x] 8. 使用前端与契约模拟 API 验证桌面/移动端和明暗主题；真实后端因本机缺 PostgreSQL 无法启动。

## Validation Commands

```powershell
cd backend
cmd /c mvnw.cmd -q "-Dtest=EastmoneyJsParserRealtimeTest,MarketRealtimeCacheTest,MarketRealtimeRedisStoreTest,MarketBreadthViewTest,RealtimeMarketOverviewQueryHandlerTest" test

cd ..\frontend
npm test -- --run
npm run lint
npm run build

cd ..
git diff --check
```

## Validation Result

- 后端上述定向测试通过。
- 前端 29 个测试文件、101 个测试通过；lint 和构建通过。
- 桌面/移动端、明/暗主题预览通过，无 body 横向溢出。
- 后端完整测试运行 509 个，80 个集成测试因本机 `localhost:5432` 无 PostgreSQL 及 Testcontainers 无可用 Docker 环境报错；0 个断言失败。
- 真实后端启动同样被本机 `localhost:5432` 无 PostgreSQL 阻断。

## Review Gates

- Gate A：任一固定市场 `f106` 缺失时不发布市场宽度，旧 Redis 快照不误恢复。
- Gate B：指数、宽度、行业任一刷新失败时 `updatedAt` 不前移；市场状态覆盖交易日与非交易日边界。
- Gate C：持仓贡献、收益率和行业净占比都只派生现有字段，null/0 不显示伪造数值。
- Gate D：完整前后端验证和四类视口/主题预览通过，工作区只包含本任务改动。

## Rollback Points

- 后端字段与缓存改动无数据库迁移，可整组回退。
- 前端各模块只消费兼容新增字段，可独立回退到原工作台。
