package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测策略模拟器(issue #11):在历史净值序列上模拟执行某版策略参数。
 *
 * <h3>简化版规则(#12 信号引擎完成前的临时内联子集)</h3>
 * <ul>
 *   <li>建仓/加仓:从持仓期峰值(peak)回撤达 tier1/2/3/4 阈值时,按对应比例加仓</li>
 *   <li>每档阈值整轮回测只触发一次(避免反复加仓);#12 完成后若需多轮刷新再扩展</li>
 *   <li>移动止盈:持仓市值从持仓期峰值回落 ≥ stopLossPullbackPercent 时全部卖出</li>
 *   <li>加仓金额 = plannedTotalAmount × tierRatio(单档预算)</li>
 * </ul>
 *
 * <p>#12 完成后,本模拟器应重构为调用 {@code DisciplineStrategyService.evaluateSignal} 的核心逻辑。
 */
public final class BacktestSimulator {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private BacktestSimulator() {
    }

    /**
     * @param navSequence 累计净值序列(按日期升序)
     * @param params      策略参数
     * @return 策略收益率 + 逐日市值序列
     */
    public static BacktestResult simulate(List<BigDecimal> navSequence, BacktestParams params) {
        if (navSequence == null || navSequence.isEmpty()) {
            return new BacktestResult(BigDecimal.ZERO, List.of());
        }
        BigDecimal shares = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal peakNav = BigDecimal.ZERO;
        BigDecimal peakMarketValue = BigDecimal.ZERO;
        BigDecimal cashAfterExit = null; // 止盈后持有的现金(份额清零,不再随净值变)
        boolean[] tierFired = new boolean[4];
        List<BigDecimal> dailyValues = new ArrayList<>(navSequence.size());

        for (BigDecimal nav : navSequence) {
            if (cashAfterExit != null) {
                // 已止盈清仓,市值固定为现金
                dailyValues.add(cashAfterExit);
                continue;
            }
            if (nav.compareTo(peakNav) > 0) {
                peakNav = nav;
            }
            BigDecimal drawdown = peakNav.subtract(nav).divide(peakNav, MATH);
            // 加仓判定:四档回撤阈值
            if (!tierFired[0] && drawdown.compareTo(params.tier1Drawdown()) >= 0) {
                BigDecimal amount = params.plannedTotalAmount().multiply(params.tier1Ratio(), MATH);
                shares = shares.add(amount.divide(nav, MATH));
                invested = invested.add(amount);
                tierFired[0] = true;
            }
            if (!tierFired[1] && drawdown.compareTo(params.tier2Drawdown()) >= 0) {
                BigDecimal amount = params.plannedTotalAmount().multiply(params.tier2Ratio(), MATH);
                shares = shares.add(amount.divide(nav, MATH));
                invested = invested.add(amount);
                tierFired[1] = true;
            }
            if (!tierFired[2] && drawdown.compareTo(params.tier3Drawdown()) >= 0) {
                BigDecimal amount = params.plannedTotalAmount().multiply(params.tier3Ratio(), MATH);
                shares = shares.add(amount.divide(nav, MATH));
                invested = invested.add(amount);
                tierFired[2] = true;
            }
            if (!tierFired[3] && drawdown.compareTo(params.tier4Drawdown()) >= 0) {
                BigDecimal amount = params.plannedTotalAmount().multiply(params.tier4Ratio(), MATH);
                shares = shares.add(amount.divide(nav, MATH));
                invested = invested.add(amount);
                tierFired[3] = true;
            }
            // 移动止盈:持仓市值从峰值回落 >= 阈值 → 全部卖出,锁定现金
            BigDecimal marketValue = shares.multiply(nav, MATH);
            if (marketValue.compareTo(peakMarketValue) > 0) {
                peakMarketValue = marketValue;
            }
            if (peakMarketValue.signum() > 0 && invested.signum() > 0) {
                BigDecimal pullback = peakMarketValue.subtract(marketValue).divide(peakMarketValue, MATH);
                if (pullback.compareTo(params.stopLossPullbackPercent()) >= 0) {
                    cashAfterExit = marketValue;
                    shares = BigDecimal.ZERO;
                    peakMarketValue = BigDecimal.ZERO;
                    marketValue = cashAfterExit;
                }
            }
            dailyValues.add(marketValue);
        }
        BigDecimal finalValue = cashAfterExit != null
                ? cashAfterExit
                : (shares.signum() > 0
                        ? shares.multiply(navSequence.get(navSequence.size() - 1), MATH)
                        : BigDecimal.ZERO);
        BigDecimal strategyReturn = invested.signum() > 0
                ? finalValue.subtract(invested).divide(invested, MATH)
                : BigDecimal.ZERO;
        return new BacktestResult(strategyReturn, dailyValues);
    }
}
