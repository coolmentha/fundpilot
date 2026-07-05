package com.fundpilot.backend.fund.event;

/**
 * 基金创建成功事件:用于事务提交后异步补齐历史净值。
 *
 * @param fundId 新建基金 ID
 */
public record FundCreatedEvent(Long fundId) {
}
