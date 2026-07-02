package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 定投移动止盈回测模拟器(issue #57):逐日在历史净值序列上模拟「等额定投 + High 移动止盈」,
 * 与生产信号引擎共用 {@link TrailingStopEngine}——测回测等于测策略本体(PRD Testing Decisions)。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>每月最后交易日扣款产生 INVEST 交易,金额 = 每期定投金额(等额、与 dca 基准同口径)。</li>
 *   <li>逐日调 {@link TrailingStopEngine#evaluate} 判定止盈卖出;SELL 即执行(回测假设当日净值确认)。</li>
 *   <li>High 实时派生(max 市值序列),不落库;卖出后以剩余仓位重算 High。</li>
 *   <li>总市值序列 = 落袋现金 + 持仓市值,用于算最大回撤与 Calmar。</li>
 * </ul>
 */
public final class DcaTakeProfitSimulator {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 行业止盈后暂停定投,收益率跌回此值(10%)以下才恢复(CONTEXT.md「定投暂停与恢复」)。 */
    private static final BigDecimal DCA_RESUME_YIELD = new BigDecimal("0.10");

    private DcaTakeProfitSimulator() {
    }

    /**
     * @param navSequence 累计净值序列(升序)
     * @param navDates    对应日期(UTC,等长)
     * @param dcaAmount    每期定投金额(每月最后交易日扣款)
     * @param params       移动止盈参数(按 fundCategory 分派)
     * @param fundCategory 基金类型:行业止盈后暂停定投、收益跌回 10% 以下恢复;宽基一直定投不停
     * @return 策略收益率 + 逐日总市值序列 + 交易记录
     */
    /** 4 参重载:默认宽基(不暂停定投),兼容 #57 单测。 */
    public static DcaTakeProfitResult simulate(List<BigDecimal> navSequence, List<Instant> navDates,
                                               BigDecimal dcaAmount, TakeProfitParams params) {
        return simulate(navSequence, navDates, dcaAmount, params, com.fundpilot.backend.fund.enums.FundCategory.BROAD_BASE);
    }

    public static DcaTakeProfitResult simulate(List<BigDecimal> navSequence, List<Instant> navDates,
                                               BigDecimal dcaAmount, TakeProfitParams params,
                                               com.fundpilot.backend.fund.enums.FundCategory fundCategory) {        if (navSequence == null || navSequence.isEmpty()) {
            return new DcaTakeProfitResult(BigDecimal.ZERO, List.of(), List.of());
        }
        Set<Integer> monthEndIndices = monthEndIndices(navDates);
        boolean sectorDcaPauseEnabled = fundCategory == com.fundpilot.backend.fund.enums.FundCategory.SECTOR;

        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal holdingShares = BigDecimal.ZERO;
        BigDecimal totalAcquiredShares = BigDecimal.ZERO;
        BigDecimal totalSoldShares = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal cashedOut = BigDecimal.ZERO;
        boolean dcaPaused = false; // 行业止盈后暂停定投;宽基恒 false

        List<DcaTakeProfitResult.SimTrade> trades = new ArrayList<>();
        List<BigDecimal> dailyValues = new ArrayList<>(navSequence.size());

        for (int i = 0; i < navSequence.size(); i++) {
            BigDecimal nav = navSequence.get(i);
            Instant date = i < navDates.size() ? navDates.get(i) : Instant.EPOCH;

            // 行业定投恢复判定:暂停中且收益率跌回 10% 以下 → 恢复(市场冷却)
            if (dcaPaused && invested.signum() > 0) {
                BigDecimal mv = holdingShares.multiply(nav, MATH);
                BigDecimal yield = mv.subtract(invested).divide(invested, MATH);
                if (yield.compareTo(DCA_RESUME_YIELD) <= 0) {
                    dcaPaused = false;
                }
            }

            // 每月最后交易日扣款产生 INVEST(定投不经信号引擎);行业暂停期跳过
            if (monthEndIndices.contains(i) && dcaAmount != null && dcaAmount.signum() > 0 && !dcaPaused) {
                BigDecimal shares = dcaAmount.divide(nav, MATH);
                holdingShares = holdingShares.add(shares, MATH);
                totalAcquiredShares = totalAcquiredShares.add(shares, MATH);
                invested = invested.add(dcaAmount, MATH);
                trades.add(new DcaTakeProfitResult.SimTrade(date, "INVEST", dcaAmount, shares, nav));
            }

            TrailingStopEngine.Position position = new TrailingStopEngine.Position(
                    holdingShares, totalAcquiredShares, totalSoldShares, invested, cashedOut);
            TrailingStopEngine.Step step = TrailingStopEngine.evaluate(state, position, nav, params);

            if (step.decision() == TrailingStopEngine.Decision.SELL) {
                BigDecimal sellShares = step.sellShares();
                BigDecimal sellAmount = sellShares.multiply(nav, MATH);
                holdingShares = holdingShares.subtract(sellShares, MATH);
                totalSoldShares = totalSoldShares.add(sellShares, MATH);
                cashedOut = cashedOut.add(sellAmount, MATH);
                trades.add(new DcaTakeProfitResult.SimTrade(date, "SELL", sellAmount, sellShares, nav));
                // 行业止盈后暂停定投(下跌途中不加仓),收益跌回 10% 以下才恢复(见循环顶部判定)
                if (sectorDcaPauseEnabled) {
                    dcaPaused = true;
                }
            }
            state = step.newState();

            BigDecimal marketValue = holdingShares.multiply(nav, MATH);
            dailyValues.add(cashedOut.add(marketValue, MATH));
        }

        BigDecimal lastNav = navSequence.get(navSequence.size() - 1);
        BigDecimal finalValue = cashedOut.add(holdingShares.multiply(lastNav, MATH), MATH);
        BigDecimal strategyReturn = invested.signum() > 0
                ? finalValue.subtract(invested).divide(invested, MATH)
                : BigDecimal.ZERO;
        return new DcaTakeProfitResult(strategyReturn, List.copyOf(dailyValues), List.copyOf(trades));
    }

    /** 每月最后交易日下标:按(年×12+月)分组取每月最后一个净值日的下标。用 ZonedDateTime 派生月份键,不引入 LocalDate/YearMonth。 */
    private static Set<Integer> monthEndIndices(List<Instant> navDates) {
        LinkedHashMap<Integer, Integer> lastDayOfMonth = new LinkedHashMap<>();
        for (int i = 0; i < navDates.size(); i++) {
            java.time.ZonedDateTime zdt = navDates.get(i).atZone(java.time.ZoneOffset.UTC);
            int monthKey = zdt.getYear() * 12 + zdt.getMonthValue();
            lastDayOfMonth.put(monthKey, i);
        }
        return new HashSet<>(lastDayOfMonth.values());
    }
}
