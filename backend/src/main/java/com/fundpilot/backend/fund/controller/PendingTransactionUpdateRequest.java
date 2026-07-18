package com.fundpilot.backend.fund.controller;

import java.math.BigDecimal;
import java.time.Instant;

/** PENDING 流水可编辑字段；来源、基金和关联关系保持不变。 */
public record PendingTransactionUpdateRequest(
        BigDecimal amount,
        BigDecimal shares,
        Instant tradeDate) {
}
