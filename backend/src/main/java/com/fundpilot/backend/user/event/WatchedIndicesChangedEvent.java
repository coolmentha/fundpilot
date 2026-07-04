package com.fundpilot.backend.user.event;

/**
 * 用户关注指数列表变更事件。
 *
 * <p>配置保存成功后由 {@code UserConfigService.update} 发布,行情缓存层监听后即时刷新指数缓存,
 * 让前端不必等下一个 30s cron 周期就能看到新关注列表的行情(尤其在非交易时段,cron 不跑,
 * 没有事件就永远看不到)。
 *
 * <p>用 Spring 事件而非直接注入缓存,避免 UserConfigService ↔ MarketRealtimeCache 循环依赖
 * (后者读 getWatchedIndices,前者若直接调后者就会成环)。
 */
public record WatchedIndicesChangedEvent() {
}
