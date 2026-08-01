package com.fundpilot.backend.insights.adapter.web.portfolioreturn;

import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnTrendQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights/portfolio")
@RequiredArgsConstructor
public class PortfolioReturnController {
    private final PortfolioReturnQueryHandler queries;
    private final PortfolioReturnTrendQueryHandler trends;

    @GetMapping("/returns")
    public InsightsApiResponse<PortfolioReturnView> returns(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        var value = queries.findByOwner(ownerId);
        return InsightsApiResponse.ok(new PortfolioReturnView(value.investedAmount(), value.redeemedAmount(),
                value.feeAmount(), value.holdingAmount(), value.realizedPnl(), value.unrealizedPnl(),
                value.totalReturn(), value.returnRate(), value.realizedComplete(), value.funds().stream()
                .map(FundReturnView::from).toList()));
    }

    @GetMapping("/funds/current")
    public InsightsApiResponse<List<FundReturnView>> currentFunds(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return InsightsApiResponse.ok(queries.currentFunds(ownerId).stream().map(FundReturnView::from).toList());
    }

    @GetMapping("/funds/history")
    public InsightsApiResponse<List<FundReturnView>> clearedFunds(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return InsightsApiResponse.ok(queries.clearedFunds(ownerId).stream().map(FundReturnView::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}")
    public InsightsApiResponse<FundReturnView> fund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @org.springframework.web.bind.annotation.PathVariable long legacyFundId) {
        var fund = queries.fund(ownerId, legacyFundId);
        return InsightsApiResponse.ok(fund == null ? null : FundReturnView.from(fund));
    }

    @GetMapping("/summary")
    public InsightsApiResponse<PortfolioReturnQueryHandler.PortfolioSummaryResult> summary(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return InsightsApiResponse.ok(queries.summary(ownerId));
    }

    @GetMapping("/return-trends")
    public InsightsApiResponse<PortfolioReturnTrendQueryHandler.TrendResult> returnTrends(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @RequestParam(defaultValue = "30D") String period,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return InsightsApiResponse.ok(trends.find(ownerId, period, from, to));
    }

    public record PortfolioReturnView(BigDecimal investedAmount, BigDecimal redeemedAmount, BigDecimal feeAmount,
                                      BigDecimal holdingAmount, BigDecimal realizedPnl, BigDecimal unrealizedPnl,
                                      BigDecimal totalReturn, BigDecimal returnRate, boolean realizedComplete,
                                      List<FundReturnView> funds) {}
    public record FundReturnView(Long id, long portfolioFundId, String fundCode, String fundName,
                                 String fundCategory, String fundSubType, String status,
                                 String positionStatus, String investmentTarget, String benchmarkIndexCode,
                                 boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                 BigDecimal investedAmount, BigDecimal redeemedAmount,
                                 BigDecimal feeAmount, BigDecimal holdingAmount, BigDecimal realizedPnl,
                                 BigDecimal unrealizedPnl, BigDecimal totalReturn, BigDecimal returnRate,
                                 boolean realizedComplete, Instant valuationDate, List<FundGroupView> groups,
                                 BigDecimal holdingShares, BigDecimal costPerShare, BigDecimal dailyChangePct,
                                 BigDecimal dailyPnl, BigDecimal totalPnl, boolean isEstimated,
                                 boolean estimateFetchFailed, String estimateStatus, BigDecimal valuationNav,
                                 String valuationSource, Instant valuationFirstSeenAt, String estimateTime,
                                 String baseNavDate, Instant openedAt) {
        static FundReturnView from(PortfolioReturnQueryHandler.FundReturnResult value) {
            return new FundReturnView(value.legacyFundId(), value.portfolioFundId(), value.fundCode(),
                    value.fundName(), value.disciplineCategory(), value.productType(),
                    switch (value.positionStatus()) { case "OPEN" -> "HOLDING"; case "CLEARED" -> "CLEARED";
                        default -> "WATCHING"; }, value.positionStatus(), value.investmentTarget(),
                    value.benchmarkIndexCode(), value.positionWarningEnabled(),
                    value.positionWarningRatio(), value.investedAmount(), value.redeemedAmount(),
                    value.feeAmount(), value.holdingAmount(), value.realizedPnl(), value.unrealizedPnl(),
                    value.totalReturn(), value.returnRate(), value.realizedComplete(), value.valuationDate(),
                    value.groups().stream().map(group -> new FundGroupView(group.id(), group.name())).toList(),
                    value.holdingShares(), value.costPerShare(), value.dailyChangePct(), value.dailyPnl(),
                    value.totalReturn(), value.estimated(), value.estimateFetchFailed(), value.estimateStatus(),
                    value.valuationNav(), value.valuationSource(), value.valuationFirstSeenAt(),
                    value.estimateTime(), value.baseNavDate(), value.openedAt());
        }
    }
    public record FundGroupView(long id, String name) {}
}
