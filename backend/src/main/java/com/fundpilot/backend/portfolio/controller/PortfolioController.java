package com.fundpilot.backend.portfolio.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.FundPnlService;
import com.fundpilot.backend.portfolio.service.PortfolioReturnService;
import com.fundpilot.backend.portfolio.service.PortfolioReturnTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/**
 * 组合概览端点(issue #18):返回持仓组合的盈亏汇总(今日盈亏合计 + 涨跌/盈亏基金计数)。
 * <p>Controller 只做 HTTP 路由,聚合逻辑在 {@link FundPnlService}。
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final FundPnlService fundPnlService;
    private final PortfolioReturnService portfolioReturnService;
    private final PortfolioReturnTrendService portfolioReturnTrendService;

    /** 组合盈亏汇总(概览页 KPI 用)。 */
    @GetMapping("/summary")
    public ApiResponse<PortfolioSummaryView> summary() {
        return ApiResponse.ok(PortfolioSummaryView.from(fundPnlService.computePortfolioSummary()));
    }

    @GetMapping("/returns")
    public ApiResponse<PortfolioReturnView> returns() {
        return ApiResponse.ok(portfolioReturnService.getReturns());
    }

    @GetMapping("/return-trends")
    public ApiResponse<PortfolioReturnTrendView> returnTrends(
            @RequestParam(defaultValue = "30D") String period,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ApiResponse.ok(portfolioReturnTrendService.getTrend(period, from, to));
    }
}
