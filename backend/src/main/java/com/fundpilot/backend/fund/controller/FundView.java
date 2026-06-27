package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.enums.InvestmentPhilosophy;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.enums.OperationMode;
import com.fundpilot.backend.fund.service.FundPnlService;

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
 * @param dailyChangePct       今日涨跌幅(issue #18,可空——无净值历史时为 null)
 * @param holdingShares        持仓份额(issue #18,可空——无持仓时为 null)
 * @param holdingAmount        持仓市值(issue #18,可空——无持仓或无净值时为 null)
 * @param dailyPnl             今日盈亏(issue #18,可空——无持仓或无净值时为 null)
 * @param totalPnl             总盈亏(issue #18,可空——无持仓或无净值时为 null)
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
        BigDecimal dailyChangePct,
        BigDecimal holdingShares,
        BigDecimal holdingAmount,
        BigDecimal dailyPnl,
        BigDecimal totalPnl,
        Instant createdDate) {

    /** 从 Entity 映射到视图 DTO(盈亏字段为 null,供新建/更新等不需盈亏的场景用)。 */
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
                null, null, null, null, null,
                fund.getCreatedDate());
    }

    /** 从 Entity + 盈亏结果映射到视图 DTO(列表/详情等需展示盈亏的场景用)。 */
    public static FundView from(FundEntity fund, FundPnlService.Pnl pnl) {
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
                pnl.dailyChangePct(),
                pnl.holdingShares(),
                pnl.holdingAmount(),
                pnl.dailyPnl(),
                pnl.totalPnl(),
                fund.getCreatedDate());
    }
}
