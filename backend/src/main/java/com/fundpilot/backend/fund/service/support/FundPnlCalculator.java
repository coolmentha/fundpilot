package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/**
 * 盈亏与涨跌纯算术计算器(issue #18,CONTEXT.md「今日涨跌/今日盈亏/总盈亏」)。
 * <p>无 Spring/DB 依赖,所有外部值由调用方(Service)预注入,便于单测构造数值覆盖各分支。
 * <p>统一用累计净值 accumulatedNav——分红除权会让单位净值跌幅"虚高"。
 * 入参为 null 或上一期净值为零(除零)时返回 null,对应 View 字段可空。
 *
 * <h3>三公式</h3>
 * <ul>
 *   <li>今日涨跌幅 = (最近累计净值 - 上一期累计净值) / 上一期累计净值</li>
 *   <li>今日盈亏 = 持仓份额 × (最近累计净值 - 上一期累计净值)</li>
 *   <li>总盈亏 = 持仓份额 × 最近累计净值 - 持仓成本</li>
 * </ul>
 */
public final class FundPnlCalculator {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private FundPnlCalculator() {
    }

    /**
     * 今日涨跌幅 = (最近累计净值 - 上一期累计净值) / 上一期累计净值。
     * 上一期净值为 0 或任一入参为 null → null(防除零)。
     */
    public static BigDecimal dailyChangePct(BigDecimal latestAccumulatedNav, BigDecimal previousAccumulatedNav) {
        if (latestAccumulatedNav == null || previousAccumulatedNav == null
                || previousAccumulatedNav.signum() == 0) {
            return null;
        }
        return latestAccumulatedNav.subtract(previousAccumulatedNav)
                .divide(previousAccumulatedNav, MATH);
    }

    /**
     * 今日盈亏 = 持仓份额 × (最近累计净值 - 上一期累计净值)。
     * 无持仓(份额 null)或净值缺一期 → null(今日盈亏仅对持仓有意义)。
     */
    public static BigDecimal dailyPnl(BigDecimal holdingShares, BigDecimal latestAccumulatedNav, BigDecimal previousAccumulatedNav) {
        if (holdingShares == null || latestAccumulatedNav == null || previousAccumulatedNav == null) {
            return null;
        }
        return holdingShares.multiply(latestAccumulatedNav.subtract(previousAccumulatedNav), MATH);
    }

    /**
     * 总盈亏 = 持仓份额 × 最近累计净值 - 持仓成本(当前市值 - 成本)。
     * 无持仓或无最近净值 → null;成本为 null 视为 0(纯转入未买入等边界)。
     */
    public static BigDecimal totalPnl(BigDecimal holdingShares, BigDecimal latestAccumulatedNav, BigDecimal cost) {
        if (holdingShares == null || latestAccumulatedNav == null) {
            return null;
        }
        BigDecimal marketValue = holdingShares.multiply(latestAccumulatedNav, MATH);
        BigDecimal effectiveCost = cost != null ? cost : BigDecimal.ZERO;
        return marketValue.subtract(effectiveCost);
    }

    /**
     * 今日盈亏(三态统一口径,issue #38)= 昨日市值 × 今日涨跌幅。
     * <p>昨日市值 = 持仓份额 × 上一期累计净值(previousNav,确定的基线);
     * 乘今日涨跌幅(盘后=实际、盘中=fundgz 估算)得今日盈亏。
     * 与盘后态 {@link #dailyPnl}(份额×(latest-previous))数学等价,但盘中态用估算涨跌幅也能算。
     * 无持仓/无上一期净值/无涨跌幅 → null。
     */
    public static BigDecimal dailyPnlByChangePct(BigDecimal holdingShares, BigDecimal previousAccumulatedNav,
                                                 BigDecimal todayChangePct) {
        if (holdingShares == null || previousAccumulatedNav == null || todayChangePct == null) {
            return null;
        }
        BigDecimal yesterdayMarketValue = holdingShares.multiply(previousAccumulatedNav, MATH);
        return yesterdayMarketValue.multiply(todayChangePct, MATH);
    }

    /**
     * 盘中估算总盈亏(issue #38)= 昨日总盈亏 × (1 + 今日涨跌幅)。
     * <p>昨日总盈亏 = 持仓份额 × 上一期累计净值 - 成本(确定的基线);
     * 乘 (1+今日涨跌幅) 推算盘中总盈亏。口径与今日盈亏同源(都用涨跌幅比例),不引入单位净值 gsz。
     * 无持仓/无上一期净值/无涨跌幅 → null;成本为 null 视为 0。
     */
    public static BigDecimal estimatedTotalPnl(BigDecimal holdingShares, BigDecimal previousAccumulatedNav,
                                               BigDecimal cost, BigDecimal todayChangePct) {
        if (holdingShares == null || previousAccumulatedNav == null || todayChangePct == null) {
            return null;
        }
        BigDecimal yesterdayMarketValue = holdingShares.multiply(previousAccumulatedNav, MATH);
        BigDecimal effectiveCost = cost != null ? cost : BigDecimal.ZERO;
        BigDecimal yesterdayTotalPnl = yesterdayMarketValue.subtract(effectiveCost);
        return yesterdayTotalPnl.multiply(BigDecimal.ONE.add(todayChangePct, MATH), MATH);
    }

    /**
     * 组合盈亏聚合(issue #18 概览页):汇总一组基金的今日盈亏合计与涨跌/盈亏基金计数。
     * <p>三个列表按基金一一对应(同一下标为同一只基金的三项指标);null 元素跳过不计。
     * 上涨/下跌按今日涨跌幅符号,盈利/亏损按总盈亏符号——两维度独立(故事 24)。
     *
     * @param dailyChangePcts 各基金今日涨跌幅(可含 null)
     * @param dailyPnls        各基金今日盈亏(可含 null,合计时视为 0)
     * @param totalPnls        各基金总盈亏(可含 null)
     * @return 五指标汇总
     */
    public static PortfolioSummary summarize(
            List<BigDecimal> dailyChangePcts, List<BigDecimal> dailyPnls, List<BigDecimal> totalPnls) {
        BigDecimal dailyPnlTotal = BigDecimal.ZERO;
        int rising = 0, falling = 0, profitable = 0, losing = 0;
        for (int i = 0; i < dailyPnls.size(); i++) {
            BigDecimal dailyPnl = dailyPnls.get(i);
            if (dailyPnl != null) {
                dailyPnlTotal = dailyPnlTotal.add(dailyPnl, MATH);
            }
            BigDecimal changePct = i < dailyChangePcts.size() ? dailyChangePcts.get(i) : null;
            if (changePct != null) {
                int sign = changePct.signum();
                if (sign > 0) rising++;
                else if (sign < 0) falling++;
            }
            BigDecimal totalPnl = i < totalPnls.size() ? totalPnls.get(i) : null;
            if (totalPnl != null) {
                int sign = totalPnl.signum();
                if (sign > 0) profitable++;
                else if (sign < 0) losing++;
            }
        }
        return new PortfolioSummary(dailyPnlTotal, rising, falling, profitable, losing);
    }
}
