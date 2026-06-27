package com.fundpilot.backend.fund.service.support;

import java.math.BigDecimal;
import java.math.MathContext;

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
}
