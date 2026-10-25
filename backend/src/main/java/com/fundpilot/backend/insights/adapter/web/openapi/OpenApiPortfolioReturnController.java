package com.fundpilot.backend.insights.adapter.web.openapi;

import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnTrendQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Open API v1 组合收益只读资源。 */
@Tag(name = "Open API 收益", description = "当前用户的累计收益与趋势")
@RestController
@RequestMapping("/api/open/v1/portfolio")
@RequiredArgsConstructor
public class OpenApiPortfolioReturnController {

    private final PortfolioReturnQueryHandler returns;
    private final PortfolioReturnTrendQueryHandler trends;

    @GetMapping("/summary")
    @Operation(summary = "查询持仓汇总")
    public ApiResponse<SummaryView> summary(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(SummaryView.from(returns.summary(ownerId)));
    }

    @GetMapping("/returns")
    @Operation(summary = "查询累计收益")
    public ApiResponse<ReturnView> returns(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(ReturnView.from(returns.findByOwner(ownerId)));
    }

    @GetMapping("/returns/trends")
    @Operation(summary = "查询收益趋势")
    public ApiResponse<TrendView> trends(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                         @RequestParam(defaultValue = "30D") String period,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(TrendView.from(trends.find(ownerId, period, from, to)));
    }

    public record SummaryView(BigDecimal holdingAmountTotal, BigDecimal dailyPnlTotal,
                              BigDecimal dailyChangePct, BigDecimal totalPnlTotal, int holdingFundCount,
                              int dailyCoveredFundCount, int risingFundCount, int fallingFundCount,
                              int profitableFundCount, int losingFundCount, boolean isEstimated,
                              int estimateFetchFailedCount) {
        static SummaryView from(PortfolioReturnQueryHandler.PortfolioSummaryResult value) {
            return new SummaryView(value.holdingAmountTotal(), value.dailyPnlTotal(), value.dailyChangePct(),
                    value.totalPnlTotal(), value.holdingFundCount(), value.dailyCoveredFundCount(),
                    value.risingFundCount(), value.fallingFundCount(), value.profitableFundCount(),
                    value.losingFundCount(), value.isEstimated(), value.estimateFetchFailedCount());
        }
    }

    public record ReturnView(BigDecimal investedAmount, BigDecimal redeemedAmount, BigDecimal feeAmount,
                             BigDecimal holdingAmount, BigDecimal realizedPnl, BigDecimal unrealizedPnl,
                             BigDecimal totalReturn, BigDecimal returnRate, boolean realizedComplete,
                             List<FundReturnView> funds) {
        static ReturnView from(PortfolioReturnQueryHandler.PortfolioReturnResult value) {
            return new ReturnView(value.investedAmount(), value.redeemedAmount(), value.feeAmount(),
                    value.holdingAmount(), value.realizedPnl(), value.unrealizedPnl(), value.totalReturn(),
                    value.returnRate(), value.realizedComplete(), value.funds().stream().map(FundReturnView::from).toList());
        }
    }

    public record FundReturnView(long portfolioFundId, long fundProductId, String fundCode, String fundName,
                                 String positionStatus, String productType, String investmentTarget,
                                 String benchmarkIndexCode, String disciplineCategory, String status,
                                 BigDecimal positionWarningRatio, BigDecimal investedAmount,
                                 BigDecimal redeemedAmount, BigDecimal feeAmount, BigDecimal holdingAmount,
                                 BigDecimal realizedPnl, BigDecimal unrealizedPnl, BigDecimal totalReturn,
                                 BigDecimal returnRate, boolean realizedComplete, Instant valuationDate,
                                 BigDecimal holdingShares, BigDecimal costPerShare, BigDecimal dailyChangePct,
                                 BigDecimal dailyPnl, boolean estimated, String estimateStatus,
                                 BigDecimal valuationNav, String estimateTime, String baseNavDate, Instant openedAt) {
        static FundReturnView from(PortfolioReturnQueryHandler.FundReturnResult value) {
            return new FundReturnView(value.portfolioFundId(), value.fundProductId(), value.fundCode(),
                    value.fundName(), value.positionStatus(), value.productType(), value.investmentTarget(),
                    value.benchmarkIndexCode(), value.disciplineCategory(), value.positionStatus(),
                    value.positionWarningRatio(), value.investedAmount(), value.redeemedAmount(), value.feeAmount(),
                    value.holdingAmount(), value.realizedPnl(), value.unrealizedPnl(), value.totalReturn(),
                    value.returnRate(), value.realizedComplete(), value.valuationDate(), value.holdingShares(),
                    value.costPerShare(), value.dailyChangePct(), value.dailyPnl(), value.estimated(),
                    value.estimateStatus(), value.valuationNav(), value.estimateTime(), value.baseNavDate(),
                    value.openedAt());
        }
    }

    public record TrendView(Instant dataStartDate, Instant latestDate, boolean dataSufficient,
                            boolean valuationComplete, List<String> missingFundCodes,
                            BigDecimal intervalReturn, BigDecimal intervalReturnRate,
                            BigDecimal investedAmount, BigDecimal redeemedAmount, BigDecimal feeAmount,
                            BigDecimal maximumReturn, BigDecimal maximumDrawdown, List<TrendPoint> points) {
        static TrendView from(PortfolioReturnTrendQueryHandler.TrendResult value) {
            return new TrendView(value.dataStartDate(), value.latestDate(), value.dataSufficient(),
                    value.valuationComplete(), value.missingFundCodes(), value.intervalReturn(),
                    value.intervalReturnRate(), value.investedAmount(), value.redeemedAmount(),
                    value.feeAmount(), value.maximumReturn(), value.maximumDrawdown(),
                    value.points().stream().map(TrendPoint::from).toList());
        }
    }

    public record TrendPoint(Instant date, BigDecimal totalReturn, BigDecimal holdingAmount,
                             BigDecimal investedAmount, BigDecimal redeemedAmount, boolean valuationComplete) {
        static TrendPoint from(PortfolioReturnTrendQueryHandler.TrendPoint value) {
            return new TrendPoint(value.date(), value.totalReturn(), value.holdingAmount(),
                    value.investedAmount(), value.redeemedAmount(), value.valuationComplete());
        }
    }
}
