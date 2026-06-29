package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;

/**
 * 组合盈亏汇总结果(issue #18 概览页盈亏 KPI,CONTEXT.md「概览页盈亏 KPI」)。
 * <p>上涨/下跌与盈利/亏损是两个独立维度(故事 24):
 * <ul>
 *   <li>rising/falling 按今日涨跌幅符号(今日视角)</li>
 *   <li>profitable/losing 按总盈亏符号(整体视角)</li>
 * </ul>
 * 一只基金可能今日上涨但整体亏损,两个维度独立计数不混用。
 *
 * @param dailyPnlTotal       今日盈亏合计(所有持仓基金今日盈亏之和,null 视为 0)
 * @param risingFundCount     上涨基金数(今日涨跌幅 > 0)
 * @param fallingFundCount    下跌基金数(今日涨跌幅 < 0)
 * @param profitableFundCount 盈利基金数(总盈亏 > 0)
 * @param losingFundCount     亏损基金数(总盈亏 < 0)
 */
public record PortfolioSummary(
        BigDecimal dailyPnlTotal,
        int risingFundCount,
        int fallingFundCount,
        int profitableFundCount,
        int losingFundCount) {
}
