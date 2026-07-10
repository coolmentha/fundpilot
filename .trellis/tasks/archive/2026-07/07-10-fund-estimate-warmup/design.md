# 设计：盘后重启基金估值异步预热

## 1. 数据流

应用启动完成后保留 `MarketRealtimeCache.onApplicationReady()` 同步预热指数、板块和资金。新增独立的 `ApplicationReadyEvent` 监听方法，使用项目已有 `@Async` 在后台调用基金估值刷新。估值刷新继续遍历 `FundRepository.findAll()`，通过 `FundEstimateService` 和现有东方财富共享限流逐只获取，并写入 `estimateCache`。

`FundPnlService` 仍只读缓存，不在 `/api/funds` 或 `/api/portfolio/summary` 请求线程调用外部接口。

## 2. 今日数据缺失语义

`DailyChangeResolver` 保持三个正常状态：

- 盘前：返回 0。
- 今日净值未落库且估值存在：返回 fundgz 估值，`isEstimated=true`。
- 今日净值已落库：返回当日累计净值相对上一期的实际涨跌。

异常状态“北京时间 9:30 后，今日净值未落库且估值缺失”不再降级成 T-1 对 T-2，而是返回 `todayChangePct=null`。这样单基金展示为未知；任一持仓今日收益未知时，组合 `dailyPnlTotal` 也返回 null，前端显示 `-`，不会把昨天收益或部分合计冒充全仓今日收益。

## 3. 启动性能

不把 `refreshFundEstimates()` 放回同步启动监听。`@Async` 复用 Spring 已启用的异步执行能力，事件监听立即返回，健康检查和部署 Smoke test 不等待 N 只基金的限流请求完成。

## 4. 失败与重试

单只基金请求失败继续沿用现有降级：记录服务层 warn、跳过该只，不影响其他基金。交易时段内仍由 30 秒任务继续刷新；盘后重启依赖本次异步预热获取最后一次今日估值。

本任务不新增盘后周期重试，避免 15:00-20:00 对静态收盘估值持续执行 N 次外部请求。若一次启动预热失败，界面显示未知而非错误的昨日值。

## 5. 回滚

改动仅涉及缓存启动监听、纯函数降级和测试，无 schema 或配置变化，可直接回滚提交。异步预热异常不会阻止应用启动。
