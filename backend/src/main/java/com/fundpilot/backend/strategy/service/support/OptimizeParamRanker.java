package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 寻优 train 集排序纯函数(issue #27):风险调整收益择优标尺。
 *
 * <p>风险调整收益 = 策略收益 / 策略最大回撤(类 Calmar)。与 passed 正交——passed 是布尔门槛,
 * 风险调整收益是连续排序值。规避过拟合到激进参数(CONTEXT.md「风险调整收益」)。
 *
 * <p>给定 train 集净值序列/日期/行情指标(循环外算一次复用),对每组 {@link OptimizeParams}
 * 构造 {@link BacktestParams}(3 个 fund 不变量 plannedTotalAmount/fundCategory/fundSubType 固定 + 10 参数),
 * 调 {@link BacktestSimulator#simulate} + {@link MaxDrawdownCalculator#calculate} 算每组风险调整收益,
 * 回撤为 0 的组合跳过(避免除零)。
 *
 * <p><b>top-k 择优(issue #28)</b>:{@link #rankTopK} 返 train Calmar 前 k 名(携带 train 指标),
 * 编排层把 k 组送 test 集 passed 过滤 + test Calmar 择优落库。train 单点冠军可能是噪声产物,
 * top-k 给 test 集多个候选,从"门槛"变"择优标尺"——降低选到 train 过拟合参数的概率。
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
     * <p>等价于 {@link #rankTopK}(candidates, 1) 取第一个,保留以维持现有调用方兼容。
     *
     * @return 风险调整收益最高的一组;所有组合回撤均为 0(无法排序)时返 empty
     */
    public static Optional<OptimizeParams> rankBest(
            List<BigDecimal> navSequence, List<Instant> navDates,
            List<MarketIndicators> indicators, BigDecimal plannedTotalAmount,
            List<OptimizeParams> candidates) {
        return rankTopK(navSequence, navDates, indicators, plannedTotalAmount, candidates, 1)
                .stream().findFirst().map(RankedParam::params);
    }

    /**
     * 在 train 集上对候选参数排序,返 train Calmar 前 {@code k} 名(携带 train 指标)。
     *
     * <p>排序规则:回撤为 0 的跳过(无法算 Calmar,避免除零);回撤非 0 的按 Calmar 降序。
     * 候选不足 k 名时返实际数量。
     *
     * @param candidates #26 生成的候选参数组合
     * @param k          返回的前 k 名数量;k &le; 0 返空列表
     * @return train Calmar 降序前 k 名;所有组合回撤均为 0 时返空
     */
    public static List<RankedParam> rankTopK(
            List<BigDecimal> navSequence, List<Instant> navDates,
            List<MarketIndicators> indicators, BigDecimal plannedTotalAmount,
            List<OptimizeParams> candidates, int k) {
        if (k <= 0) {
            return List.of();
        }
        List<RankedParam> ranked = new ArrayList<>();
        for (OptimizeParams candidate : candidates) {
            BacktestParams params = toBacktestParams(candidate, plannedTotalAmount);
            BacktestResult result = BacktestSimulator.simulate(navSequence, navDates, indicators, params);
            BigDecimal maxDrawdown = MaxDrawdownCalculator.calculate(result.dailyValues());
            BigDecimal trainReturn = result.strategyReturn();
            // 回撤为 0 跳过(避免除零;无回撤的组合风险调整收益无意义)
            if (maxDrawdown.signum() <= 0) {
                continue;
            }
            BigDecimal trainCalmar = trainReturn.divide(maxDrawdown, MATH);
            ranked.add(new RankedParam(candidate, trainCalmar, trainReturn, maxDrawdown));
        }
        // Calmar 降序取前 k
        ranked.sort(Comparator.comparing(RankedParam::trainCalmar, Comparator.reverseOrder()));
        return ranked.size() <= k ? ranked : new ArrayList<>(ranked.subList(0, k));
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
