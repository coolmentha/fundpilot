package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;

import java.math.BigDecimal;

/**
 * 基金新建/更新请求 DTO(issue #16)。
 *
 * @param fundCode           基金代码(如 510300)
 * @param fundName           基金名称
 * @param fundCategory       基金类型(宽基/行业/主动/混合)
 * @param plannedTotalAmount 计划总仓位金额
 */
public record FundCreateRequest(
        String fundCode,
        String fundName,
        FundCategory fundCategory,
        BigDecimal plannedTotalAmount) {
}
