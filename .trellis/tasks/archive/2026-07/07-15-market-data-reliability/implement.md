# 行情数据可靠性与请求性能实施计划

## Implementation Checklist

- [x] 
    1. 先补失败测试：JS 结构提取、时间戳关联、货币/REIT 不兼容形态、估值状态分类、数据源空结果降级、外呼超时预算。
- [x] 
    2. 用 Jackson 结构提取替换东方财富 GraalVM 执行；GraalVM 仅保留给新浪 KLC 解码，并运行解析器测试。
- [x] 
    3. 为全部手工 Feign client 注入统一 1s/3s Options与 1 秒限流等待，禁用重试并移除 K 线手动重试。
- [x] 
    4. 实现同花顺净值、基金字典、指数 K 线三个 raw client、结构化解析器、指数代码映射和 `ThsMarketDataSource`，补
       MockWebServer 与 live smoke 测试。
- [x] 
    5. 增加基金数据能力策略，货币基金/REIT 在进入普通净值链前中性返回 unsupported。
- [x] 
    6. 引入估值状态结果，调整 `FundEstimateService`、`MarketRealtimeCache`、`FundPnlService` 和 View DTO。
- [x] 
    7. 更新前端 watchlist/portfolio 状态展示：UNAVAILABLE/STALE 中性，TIMEOUT/PARSE_ERROR 失败。
- [x] 
    8. 重构晚间净值确认：移除 fundgz 门卫，事务外拉取，按晚于本地最新日期增量短事务入库，cron 改为每 5 分钟。
- [x] 
    9. 收紧行情刷新事务边界，配置调度池并给实时刷新加防重入。
- [x] 
    10. 添加外部调用指标，修复 job histogram 配置，显式配置 Nginx 代理超时。
- [x] 
    11. 更新 `.trellis/spec/backend/market-realtime-cache.md` 和 `CONTEXT.md` 中已变化的净值确认/估值状态合同。
- [ ] 
    12. 完成全量验证和差异复核，不提交、不推送，等待用户明确授权。

## Validation Commands

```powershell
# 后端快速单元测试
cd backend
cmd /c mvnw.cmd -q "-Dtest=EastmoneyJsParserNavHistoryTest,EastmoneyJsParserFundDictTest,EastmoneyJsParserFundGzTest,MarketDataSourceChainTest,MarketRealtimeCacheTest,DailyChangeResolverTest" test

# 需要 PostgreSQL/Docker 的后端集成测试
cmd /c mvnw.cmd -q "-Dtest=DailyNavConfirmServiceTest,KlineServiceTest,FundServiceTest" test

# 后端完整验证
cmd /c mvnw.cmd verify

# 前端状态、lint、build
cd ..\frontend
npm test -- --run
npm run lint
npm run build

# 代表性外部源 smoke（本机出口结果不替代 VPS 验证）
cd ..\backend
cmd /c mvnw.cmd -q -Plive "-Dtest=EastmoneyClientLiveSmokeTest,CsindexClientLiveSmokeTest,ThsClientLiveSmokeTest" test
```

## Risky Files And Rollback Points

- `EastmoneyJsParser.java`：先单独完成解析测试，确认兼容后再移除依赖。
- `DailyNavConfirmService.java`：必须通过普通基金、FOF/QDII 滞后日期、空响应和重复执行测试。
- `MarketRealtimeCache.java` / `FundPnlService.java`：必须保持当日净值优先于估值状态的现有合同。
- `EastmoneyClientConfig.java`：所有 client 都必须使用同一 Options，避免遗漏某个域名。
- `frontend/src/querySafety.js`：保留旧 boolean 兼容路径，新增状态优先。

## Review Gates

- Gate A：解析器测试通过后确认 `EastmoneyJsParser` 不再引用 `org.graalvm`；新浪 KLC 解码器是唯一保留路径。
- Gate B：净值确认测试证明 fundgz 失败不阻断更新且滞后日期可入库。
- Gate C：超时测试覆盖东方财富失败后同花顺净值成功，以及三源 K 线最坏链路小于 15 秒。
- Gate D：前端测试证明中性不可用与真实失败文案区分。
- Gate E：`git diff --check`、完整测试、lint、build 均通过；工作区只包含本任务文件。
