package com.fundpilot.backend.portfolio.controller;

import com.fundpilot.backend.fund.service.support.PortfolioSummary;

import java.math.BigDecimal;

/**
 * 组合盈亏汇总视图(issue #18 概览页盈亏 KPI)。
 * <p>两个独立维度(故事 24):上涨/下跌(今日涨跌幅符号)与盈利/亏损(总盈亏符号)。
 *
 * @param holdingAmountTotal  全部持仓市值
 * @param dailyPnlTotal       已覆盖持仓的今日盈亏合计
 * @param dailyChangePct      已覆盖持仓的加权涨跌幅
 * @param totalPnlTotal       全部可计算持仓的总盈亏
 * @param holdingFundCount    持仓基金总数
 * @param dailyCoveredFundCount 当日数据覆盖基金数
 * @param risingFundCount     上涨基金数
 * @param fallingFundCount    下跌基金数
 * @param profitableFundCount 盈利基金数
 * @param losingFundCount     亏损基金数
 * @param isEstimated         是否包含盘中 fundgz 估算值
 * @param estimateFetchFailedCount 估值拉取失败的持仓基金数
 */
public record PortfolioSummaryView(
        BigDecimal holdingAmountTotal,
        BigDecimal dailyPnlTotal,
        BigDecimal dailyChangePct,
        BigDecimal totalPnlTotal,
        int holdingFundCount,
        int dailyCoveredFundCount,
        int risingFundCount,
        int fallingFundCount,
        int profitableFundCount,
        int losingFundCount,
        boolean isEstimated,
        int estimateFetchFailedCount) {

    /** 从聚合结果映射到视图 DTO。 */
    public static PortfolioSummaryView from(PortfolioSummary summary) {
        return new PortfolioSummaryView(
                summary.holdingAmountTotal(),
                summary.dailyPnlTotal(),
                summary.dailyChangePct(),
                summary.totalPnlTotal(),
                summary.holdingFundCount(),
                summary.dailyCoveredFundCount(),
                summary.risingFundCount(),
                summary.fallingFundCount(),
                summary.profitableFundCount(),
                summary.losingFundCount(),
                summary.isEstimated(),
                summary.estimateFetchFailedCount());
    }
}
