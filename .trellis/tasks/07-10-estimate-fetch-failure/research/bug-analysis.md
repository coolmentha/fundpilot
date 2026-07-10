## Bug Analysis: 估值失败复用旧数据

### 1. Root Cause Category

- **Category**: B - Cross-Layer Contract，兼有 D - Test Coverage Gap
- **Specific Cause**: `MarketRealtimeCache` 把基金估值套用了指数/板块“失败保留旧缓存”的通用降级策略，但估值带北京时间自然日边界，只能代表本次成功拉取的当天短时态。失败状态也没有进入 `FundPnlService -> View -> UI`，前端只能把未知显示为普通空值。

### 2. Why Fixes Failed

1. 启动异步预热只修复了“盘后重启缓存为空”，没有定义预热失败后旧值如何失效。
2. 三态收益只禁止了 T-1 对 T-2 冒充今日涨跌，但估值缺失时仍用最新已公布净值计算当前持仓市值和总盈亏，范围不完整。
3. 原测试只覆盖首次预热成功，没有覆盖“成功后空响应/异常/旧日期”和失败状态跨层展示。

### 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|----------|-----------|-----------------|--------|
| P0 | Architecture | 基金估值刷新采用本轮替换语义，失败立即删除旧值 | DONE |
| P0 | Runtime validation | 固定校验 `estimateTime` 属于北京时间当天 | DONE |
| P0 | Cross-layer contract | `FundView` 暴露失败状态，组合 View 暴露失败持仓数 | DONE |
| P0 | Test coverage | 覆盖成功后空/异常/旧日期、恢复成功、实际净值优先 | DONE |
| P1 | Documentation | 更新 CONTEXT、ADR 和 realtime-cache code-spec | DONE |

### 4. Systematic Expansion

- **Similar Issues**: 其他无持久化实时数据也应明确区分“允许陈旧展示”和“必须按自然日失效”，不能共享一句笼统降级策略。
- **Design Improvement**: 缓存层拥有数据新鲜度和失败状态，收益层只消费显式状态，不根据空值猜测失败原因。
- **Process Improvement**: 实时缓存测试必须包含“先成功、后失败”的状态转换，而不只测首次加载。

### 5. Knowledge Capture

- [x] 更新 `.trellis/spec/backend/market-realtime-cache.md`
- [x] 更新 `CONTEXT.md` 的盘中估值、今日盈亏和总盈亏契约
- [x] 更新 ADR-0008 的失败降级决定
