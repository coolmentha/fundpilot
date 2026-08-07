# 执行计划

1. 新增东方财富通用静态估值页 Feign client、Jsoup 解析器和页面行模型，复用现有请求头、超时、限流配置，并完成 AKShare 基金/ETF/LOF、腾讯及新浪相关源清单。
2. 在 `EastmoneyClientConfig`/`ThsClientConfig` 注册 client；在 `FundEstimateService` 增加 1 分钟批量页缓存，并把静态页、交易型 ETF IOPV 分支放到同花顺之后、旧 fundgz 之前。
3. 新增 AKShare `stock_zh_index_daily_tx` 对应的腾讯指数 client、解析器和 `MarketDataSource`，接入中证之后、同花顺之前；补充 CSI 空数据跳过和 sh/sz 代码映射测试。
4. 补充静态页解析、Feign 请求路径/请求头、空页/解析失败、批量复用、成功回退和现有状态兼容测试；补腾讯 parser、source、client 和 live smoke。
5. 运行定向估值测试、指数降级测试、相关远端 client 测试和 `mvn -B -DskipTests compile`。
6. 使用当前真实静态页和腾讯指数接口执行只读冒烟：确认基金估值页可解析、腾讯 sh/sz 指数有数据、CSI 主题指数明确空并继续降级。
7. 执行 `git diff --check`，复核未引入依赖、数据库或前端契约变化。

## 验证命令

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-25.0.3'
.\mvnw.cmd -B '-Dtest=EastmoneyFundEstimatePageParserTest,EastmoneyFundEstimatePageClientTest,FundEstimateServiceTest,MarketRealtimeCacheTest' test
.\mvnw.cmd -B -DskipTests compile
git diff --check
```
