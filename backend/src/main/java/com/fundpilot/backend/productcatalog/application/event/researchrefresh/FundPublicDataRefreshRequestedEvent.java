package com.fundpilot.backend.productcatalog.application.event.researchrefresh;

/**
 * 基金公开资料首采请求事件:跟踪新基金后由 {@code FundPublicDataApi} 发布,
 * 异步监听器消费(费率 + 研究资料一次采齐),避免夜间批量任务前的首日空窗。
 */
public record FundPublicDataRefreshRequestedEvent(String fundCode) {
}
