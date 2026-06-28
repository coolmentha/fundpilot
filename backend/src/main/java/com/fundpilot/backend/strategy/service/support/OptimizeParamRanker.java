package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 寻优 train 集排序纯函数(issue #27):风险调整收益择优标尺。
 *
 * <p>风险调整收益 = 策略收益 / 策略最大回撤(类 Calmar)。与 passed 正交——passed 是布尔门槛,
 * 风险调整收益是连续排序值。同等收益选回撤小者,规避过拟合到激进参数(CONTEXT.md「风险调整收益」)。
 *
 * <p>给定 train 集净值序列/日期/行情指标(循环外算一次复用),对每组 {@link OptimizeParams}
 * 构造 {@link BacktestParams}(3 个 fund 不变量 plannedTotalAmount/fundCategory/fundSubType 固定 + 10 参数),
 * 调 {@link BacktestSimulator#simulate} + {@link MaxDrawdownCalculator#calculate} 算每组风险调整收益,
 * 回撤为 0 的组合跳过(避免除零),选比值最高的一组作为最优参数。
 *
 * <p>纯函数,不接 API/DB/前端,可独立单测验证。
 *
 * @see OptimizeGridGenerator
 */
public final class OptimizeParamRanker {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private OptimizeParamRanker() {
    }

    /**
     * 在 train 集上对候选参数排序,选出风险调整收益最高的一组。
     *
     * @param navSequence        train 集累计净值序列(升序,循环外算一次复用)
     * @param navDates           train 集对应日期(升序,等长)
     * @param indicators         train 集逐日行情指标(循环外算一次复用,等长)
     * @param plannedTotalAmount 计划总仓位(fund 不变量,所有候选共用)
     * @param candidates         #26 生成的候选参数组合
     * @return 风险调整收益最高的一组;所有组合回撤均为 0(无法排序)时返 empty
     */
    public static Optional<OptimizeParams> rankBest(
            List<BigDecimal> navSequence, List<Instant> navDates,
            List<MarketIndicators> indicators, BigDecimal plannedTotalAmount,
            List<OptimizeParams> candidates) {
        OptimizeParams best = null;
        BigDecimal bestScore = null;
        for (OptimizeParams candidate : candidates) {
            BacktestParams params = toBacktestParams(candidate, plannedTotalAmount);
            BacktestResult result = BacktestSimulator.simulate(navSequence, navDates, indicators, params);
            BigDecimal maxDrawdown = MaxDrawdownCalculator.calculate(result.dailyValues());
            // 回撤为 0 跳过(避免除零;无回撤的组合风险调整收益无意义)
            if (maxDrawdown.signum() <= 0) {
                continue;
            }
            BigDecimal riskAdjustedReturn = result.strategyReturn().divide(maxDrawdown, MATH);
            if (bestScore == null || riskAdjustedReturn.compareTo(bestScore) > 0) {
                bestScore = riskAdjustedReturn;
                best = candidate;
            }
        }
        return Optional.ofNullable(best);
    }

    /** 把 OptimizeParams(搜索维度 + 默认非搜索维度 + fund 不变量)组装成 BacktestParams。 */
    private static BacktestParams toBacktestParams(OptimizeParams p, BigDecimal plannedTotalAmount) {
        return new BacktestParams(
                p.tier1Drawdown(), p.tier2Drawdown(), p.tier3Drawdown(), p.tier4Drawdown(),
                p.tier1Ratio(), p.tier2Ratio(), p.tier3Ratio(), p.tier4Ratio(),
                p.weeklyCoolDownThreshold(), p.stopLossPullbackPercent(),
                plannedTotalAmount, p.fundCategory(), p.fundSubType());
    }
}
