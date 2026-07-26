package com.fundpilot.backend.user.event;

/**
 * 旧 UserConfig 关注指数变更事件的保留类型。
 *
 * <p>V39 后关注指数由 MarketData 拥有，此事件不再发布；保留该类型仅为了让历史排障记录
 * 和已编译的扩展代码仍可识别，后续 contract migration 再删除。
 */
public record WatchedIndicesChangedEvent() {
}
