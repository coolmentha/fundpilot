package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.signal.enums.MeasureUnit;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.service.DisciplineStrategyService;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回测策略模拟器(issue #11 重构):逐日调用 {@link DisciplineStrategyService#evaluateSignal}
 * 在历史净值序列上模拟执行策略,与生产信号引擎口径一致(BUILD/ADD/SELL 三路 + 反弹清空 + 硬约束 + MIN_HOLD_DAYS)。
 *
 * <h3>核心机制</h3>
 * <ul>
 *   <li>内存构造 {@code FundEntity}(status 从 PENDING_HOLDING 推进)+ {@code FundStrategyEntity}(tierNAddedAt 随引擎 mutate)</li>
 *   <li>逐日构造 {@link MarketIndicators}(由 {@link BacktestIndicatorCalculator} 预算)+ {@link CapitalContext} 调 evaluateSignal</li>
 *   <li>信号即执行(回测假设当日净值确认):BUILD→建仓+HOLDING;ADD→加仓+置档;SELL→减仓+清档/清仓</li>
 *   <li>反弹清空副作用由 evaluateSignal 直接改内存 strategy.tierNAddedAt,无需额外处理</li>
 * </ul>
 *
 * <h3>组合占比简化</h3>
 * 单基金孤立回测,组合风控(硬约束单只/单类/总权益上限)无意义,故 CapitalContext 的占比字段
 * 填不超限小值(0.1)骗过 check5,避免 BUILD/ADD 被组合硬约束误降级;再平衡因此不触发(回测不关心组合再平衡)。
 * 真实持仓金额/份额/峰值仍如实维护,保证移动止盈/逻辑止损判定正确。
 */
public final class BacktestSimulator {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 占比骗过硬约束的小值(单只/单类/总权益上限均 ≥ 15%,0.1 永不超限)。 */
    private static final BigDecimal MOCK_POSITION_PCT = new BigDecimal("0.1");
    private static final int MIN_HOLD_DAYS = 5;

    private BacktestSimulator() {
    }

    /**
     * @param navSequence 累计净值序列(升序)
     * @param navDates    对应日期(升序,等长)
     * @param indicators  逐日行情指标(由 BacktestIndicatorCalculator 预算,等长)
     * @param params      策略参数
     * @return 策略收益率 + 逐日市值序列
     */
    public static BacktestResult simulate(
            List<BigDecimal> navSequence, List<Instant> navDates,
            List<MarketIndicators> indicators, BacktestParams params) {
        if (navSequence == null || navSequence.isEmpty()) {
            return new BacktestResult(BigDecimal.ZERO, List.of());
        }
        DisciplineStrategyService engine = new DisciplineStrategyService();
        FundEntity fund = newMemFund(params);
        FundStrategyEntity strategy = newMemStrategy(params);

        BigDecimal holdingShares = BigDecimal.ZERO;
        BigDecimal buildShares = BigDecimal.ZERO;
        Map<Integer, BigDecimal> tierAddShares = new HashMap<>();
        tierAddShares.put(1, BigDecimal.ZERO);
        tierAddShares.put(2, BigDecimal.ZERO);
        tierAddShares.put(3, BigDecimal.ZERO);
        tierAddShares.put(4, BigDecimal.ZERO);
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal peakNav = BigDecimal.ZERO;
        BigDecimal holdingPeriodPeakNav = BigDecimal.ZERO;
        BigDecimal cashAfterExit = null;
        Instant lastBuy = null;

        List<BigDecimal> dailyValues = new ArrayList<>(navSequence.size());
        for (int i = 0; i < navSequence.size(); i++) {
            BigDecimal nav = navSequence.get(i);
            Instant date = i < navDates.size() ? navDates.get(i) : Instant.EPOCH;
            MarketIndicators market = i < indicators.size() ? indicators.get(i) : null;

            if (cashAfterExit != null) {
                dailyValues.add(cashAfterExit);
                continue;
            }
            if (market == null) {
                dailyValues.add(holdingShares.multiply(nav, MATH));
                continue;
            }

            if (nav.compareTo(peakNav) > 0) {
                peakNav = nav;
            }
            if (fund.getStatus() == FundStatus.HOLDING && nav.compareTo(holdingPeriodPeakNav) > 0) {
                holdingPeriodPeakNav = nav;
            }

            BigDecimal totalEquityAmount = holdingShares.multiply(nav, MATH);
            CapitalContext capital = new CapitalContext(
                    peakNav, holdingPeriodPeakNav,
                    MOCK_POSITION_PCT, MOCK_POSITION_PCT, MOCK_POSITION_PCT,
                    totalEquityAmount, params.plannedTotalAmount(),
                    buildShares, tierAddShares, holdingShares, lastBuy);
            long tradingDaysSinceLastBuy = lastBuy == null
                    ? MIN_HOLD_DAYS + 1
                    : Math.max(0, i - indexOf(navDates, lastBuy));

            SignalResult signal = engine.evaluateSignal(fund, strategy, market, capital, date, tradingDaysSinceLastBuy);

            // 信号即执行(当日净值确认)
            if (signal.signalType() == SignalType.BUILD) {
                BigDecimal amount = signal.suggestedMeasure().getValue();
                BigDecimal shares = amount.divide(nav, MATH);
                holdingShares = holdingShares.add(shares, MATH);
                buildShares = buildShares.add(shares, MATH);
                invested = invested.add(amount, MATH);
                fund.setStatus(FundStatus.HOLDING);
                fund.setOpenedAt(date);
                lastBuy = date;
            } else if (signal.signalType() == SignalType.ADD && signal.triggerTier() != null) {
                int tier = signal.triggerTier();
                BigDecimal amount = signal.suggestedMeasure().getValue();
                BigDecimal shares = amount.divide(nav, MATH);
                holdingShares = holdingShares.add(shares, MATH);
                tierAddShares.put(tier, tierAddShares.get(tier).add(shares, MATH));
                invested = invested.add(amount, MATH);
                setTierAddedAt(strategy, tier, date);
                lastBuy = date;
            } else if (signal.signalType() == SignalType.SELL) {
                BigDecimal sharesToSell = signal.suggestedMeasure().getValue();
                holdingShares = holdingShares.subtract(sharesToSell, MATH);
                if (signal.triggerTier() != null) {
                    setTierAddedAt(strategy, signal.triggerTier(), null);
                }
                if (holdingShares.signum() <= 0) {
                    holdingShares = BigDecimal.ZERO;
                    clearAllTiers(strategy);
                    fund.setStatus(FundStatus.CLEARED);
                    cashAfterExit = totalEquityAmount;
                }
            }

            BigDecimal marketValue = holdingShares.multiply(nav, MATH);
            dailyValues.add(marketValue);
        }

        BigDecimal finalValue = cashAfterExit != null
                ? cashAfterExit
                : (holdingShares.signum() > 0
                        ? holdingShares.multiply(navSequence.get(navSequence.size() - 1), MATH)
                        : BigDecimal.ZERO);
        BigDecimal strategyReturn = invested.signum() > 0
                ? finalValue.subtract(invested).divide(invested, MATH)
                : BigDecimal.ZERO;
        return new BacktestResult(strategyReturn, List.copyOf(dailyValues));
    }

    private static FundEntity newMemFund(BacktestParams params) {
        FundEntity fund = new FundEntity();
        fund.setStatus(FundStatus.PENDING_HOLDING);
        fund.setFundCategory(params.fundCategory());
        fund.setFundSubType(params.fundSubType());
        fund.setPlannedTotalAmount(params.plannedTotalAmount());
        return fund;
    }

    private static FundStrategyEntity newMemStrategy(BacktestParams params) {
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setStatus(StrategyParamStatus.EFFECTIVE);
        strategy.setTier1Drawdown(params.tier1Drawdown());
        strategy.setTier2Drawdown(params.tier2Drawdown());
        strategy.setTier3Drawdown(params.tier3Drawdown());
        strategy.setTier4Drawdown(params.tier4Drawdown());
        strategy.setTier1Ratio(params.tier1Ratio());
        strategy.setTier2Ratio(params.tier2Ratio());
        strategy.setTier3Ratio(params.tier3Ratio());
        strategy.setTier4Ratio(params.tier4Ratio());
        strategy.setWeeklyCoolDownThreshold(params.weeklyCoolDownThreshold());
        strategy.setStopLossPullbackPercent(params.stopLossPullbackPercent());
        return strategy;
    }

    private static void setTierAddedAt(FundStrategyEntity strategy, int tier, Instant value) {
        switch (tier) {
            case 1 -> strategy.setTier1AddedAt(value);
            case 2 -> strategy.setTier2AddedAt(value);
            case 3 -> strategy.setTier3AddedAt(value);
            case 4 -> strategy.setTier4AddedAt(value);
        }
    }

    private static void clearAllTiers(FundStrategyEntity strategy) {
        strategy.setTier1AddedAt(null);
        strategy.setTier2AddedAt(null);
        strategy.setTier3AddedAt(null);
        strategy.setTier4AddedAt(null);
    }

    /** 在 navDates 里找 lastBuy 的索引(近似交易日数);找不到返 i。 */
    private static int indexOf(List<Instant> navDates, Instant lastBuy) {
        for (int j = 0; j < navDates.size(); j++) {
            if (navDates.get(j).equals(lastBuy)) {
                return j;
            }
        }
        return 0;
    }
}
