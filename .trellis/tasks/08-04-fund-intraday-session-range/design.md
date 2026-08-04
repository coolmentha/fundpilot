# 技术设计

## 边界

只调整基金当日分时的时段元数据传递和图表投影，不改变估值快照、今日涨跌、今日盈亏或东方财富单点降级。

## 数据流

```text
同花顺 vm_fd 响应
  -> ThsJsParser 解析 tradingSessions + 实际 points
  -> FundIntradayChart / Redis 快照
  -> RealtimeValuationCacheGateway
  -> /api/portfolio-funds/{id}/intraday
  -> FundIntradayChart 生成时间槽并渲染
```

## 后端契约

- `FundIntradayChart` 增加结构化交易段列表，每段使用 `start` / `end` 的 `HH:mm` 字符串。
- 解析同花顺头部的 `0930-1130,1300-1500`；非法或缺失段被忽略，实际点解析规则保持不变。
- Redis 使用记录序列化自然带出新字段；读取旧快照时缺失字段按空列表处理，不阻断估值读取。
- 应用层网关和 HTTP View 同步透传 `tradingSessions`。

## 前端时间轴

- 将每个交易段展开为包含起止分钟的分钟槽，并按顺序拼接；午休不产生槽位。
- 实际点按 `HH:mm` 覆盖对应槽位；没有实际数据的未来槽位只保留 `timestamp`，不提供 `close`/`value`，利用 klinecharts area 对非数值点的跳过行为保持空白。
- 百分比模式把首个开盘槽位的 `close` 设为 `baseNav`，把真实净值放在 area 使用的 `value` 字段；这样 y 轴基准为 `baseNav`，时间轴仍从开盘开始。
- 净值模式使用真实 `close`，不注入基准点。
- 关闭最后价标记，避免最后一个未来空槽被误当成当前价格标记；已到达曲线和数值不变。
- 无 `tradingSessions` 的旧快照回退为现有实际点列表，不硬编码时段。

## 兼容与风险

- 旧 Redis JSON 没有 `tradingSessions` 时仍可读，刷新后逐步补齐。
- 分钟范围按北京时间构造，日期仍使用接口返回的 `estimateDate`。
- 不把午休或未来槽位写回后端，不改变估值快照点集合。
- 若 klinecharts 对带 timestamp 的非数值 area 点在真实浏览器中行为不同，以组件测试和截图验证为准；必要时退回最小的前端空槽渲染方案。
