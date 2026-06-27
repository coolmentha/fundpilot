package com.fundpilot.backend.portfolio.controller;

import com.fundpilot.backend.fund.service.support.PortfolioSummary;

import java.math.BigDecimal;

/**
 * 组合盈亏汇总视图(issue #18 概览页盈亏 KPI)。
 * <p>两个独立维度(故事 24):上涨/下跌(今日涨跌幅符号)与盈利/亏损(总盈亏符号)。
 *
 * @param dailyPnlTotal       今日盈亏合计
 * @param risingFundCount     上涨基金数
 * @param fallingFundCount    下跌基金数
 * @param profitableFundCount 盈利基金数
 * @param losingFundCount     亏损基金数
 */
public record PortfolioSummaryView(
        BigDecimal dailyPnlTotal,
        int risingFundCount,
        int fallingFundCount,
        int profitableFundCount,
        int losingFundCount) {

    /** 从聚合结果映射到视图 DTO。 */
    public static PortfolioSummaryView from(PortfolioSummary summary) {
        return new PortfolioSummaryView(
                summary.dailyPnlTotal(),
                summary.risingFundCount(),
                summary.fallingFundCount(),
                summary.profitableFundCount(),
                summary.losingFundCount());
    }
}
