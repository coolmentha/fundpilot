package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.enums.InvestmentPhilosophy;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.enums.OperationMode;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 基金视图 DTO(issue #16):返回给前端的基金信息,只含业务字段,不含 version/deletedDate 等内部字段。
 *
 * @param id                   基金 ID
 * @param fundCode             基金代码(如 510300)
 * @param fundName             基金名称
 * @param fundCategory         基金类型(宽基/行业/主动/混合)
 * @param fundSubType          基金子类型(ETF/INDEX/INDEX_ENHANCED/ACTIVE,字典回填)
 * @param status               基金状态(PENDING_HOLDING/HOLDING/CLEARED)
 * @param plannedTotalAmount   计划总仓位金额
 * @param benchmarkIndexCode   基准指数代码
 * @param investmentTarget     投资目标
 * @param operationMode        运作方式
 * @param investmentPhilosophy 投资理念
 * @param openedAt             建仓时间
 * @param createdDate          创建时间
 */
public record FundView(
        Long id,
        String fundCode,
        String fundName,
        FundCategory fundCategory,
        FundSubType fundSubType,
        FundStatus status,
        BigDecimal plannedTotalAmount,
        String benchmarkIndexCode,
        InvestmentTarget investmentTarget,
        OperationMode operationMode,
        InvestmentPhilosophy investmentPhilosophy,
        Instant openedAt,
        Instant createdDate) {

    /** 从 Entity 映射到视图 DTO。 */
    public static FundView from(FundEntity fund) {
        return new FundView(
                fund.getId(),
                fund.getFundCode(),
                fund.getFundName(),
                fund.getFundCategory(),
                fund.getFundSubType(),
                fund.getStatus(),
                fund.getPlannedTotalAmount(),
                fund.getBenchmarkIndexCode(),
                fund.getInvestmentTarget(),
                fund.getOperationMode(),
                fund.getInvestmentPhilosophy(),
                fund.getOpenedAt(),
                fund.getCreatedDate());
    }
}
