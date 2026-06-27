package com.fundpilot.backend.portfolio.controller;

import com.fundpilot.backend.fund.service.FundPnlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 组合概览端点(issue #18):返回持仓组合的盈亏汇总(今日盈亏合计 + 涨跌/盈亏基金计数)。
 * <p>Controller 只做 HTTP 路由,聚合逻辑在 {@link FundPnlService}。
 */
@RestController
@RequestMapping("/api/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final FundPnlService fundPnlService;

    /** 组合盈亏汇总(概览页 KPI 用)。 */
    @GetMapping("/summary")
    public PortfolioSummaryView summary() {
        return PortfolioSummaryView.from(fundPnlService.computePortfolioSummary());
    }
}
