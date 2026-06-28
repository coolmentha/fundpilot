package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;

import java.math.BigDecimal;

/**
 * 基金新建/更新请求 DTO(issue #16 + ADR-0005)。
 * <p>新建时 fundCode/fundName/fundSubType/fundCategory/benchmarkIndexCode 由前端从字典搜索候选带入
 * (CONTEXT.md「基金字典搜索」);plannedTotalAmount 用户手填。fundCode/fundName 二选一即可,
 * 其余类型字段可缺省(尽力填+可覆盖,缺省时由后端兜底)。
 *
 * @param fundCode             基金代码(如 510300)
 * @param fundName             基金名称
 * @param fundCategory         基金类型(宽基/行业/主动/混合)
 * @param fundSubType          基金子类型(ETF/INDEX/INDEX_ENHANCED/ACTIVE)
 * @param benchmarkIndexCode   跟踪指数代码(如 000300.SH)
 * @param plannedTotalAmount   计划总仓位金额
 */
public record FundCreateRequest(
        String fundCode,
        String fundName,
        FundCategory fundCategory,
        FundSubType fundSubType,
        String benchmarkIndexCode,
        BigDecimal plannedTotalAmount) {
}
