package com.fundpilot.backend.strategy.service.support;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 寻优 train 集排序纯函数(ADR-0015 重写,issue #63):风险调整收益(Calmar)择优标尺。
 * <p>对每组 {@link OptimizeParams} 调 {@link DcaTakeProfitSimulator} 在 train 集回测,
 * 算策略收益/最大回撤/Calmar,按 Calmar 降序返前 k 名。回撤为 0 的组合 Calmar 视 +∞(null),优先。
 * <p>纯函数,不接 API/DB/前端。
 *
 * @see OptimizeGridGenerator
 */
public final class OptimizeParamRanker {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private OptimizeParamRanker() {
    }

    /**
     * 在 train 集上对候选参数排序,选出 Calmar 最高的一组(等价 rankTopK(..., 1).findFirst())。
     *
     * @return Calmar 最高的一组;所有组合回撤均为 0(无法排序)时返 empty
     */
    public static Optional<OptimizeParams> rankBest(
            List<BigDecimal> navSequence, List<Instant> navDates,
            BigDecimal dcaAmount, com.fundpilot.backend.fund.enums.FundCategory fundCategory,
            List<OptimizeParams> candidates) {
        return rankTopK(navSequence, navDates, dcaAmount, fundCategory, candidates, 1)
                .stream().findFirst().map(RankedParam::params);
    }

    /**
     * 在 train 集上对候选参数排序,返 Calmar 降序前 {@code k} 名。
     * <p>排序:回撤为 0(Calmar +∞,null)优先;回撤非 0 按 Calmar 降序。候选不足 k 名返实际数量。
     *
     * @param candidates 网格生成的候选参数
     * @param k          返回前 k 名;k ≤ 0 返空
     */
    public static List<RankedParam> rankTopK(
            List<BigDecimal> navSequence, List<Instant> navDates,
            BigDecimal dcaAmount, com.fundpilot.backend.fund.enums.FundCategory fundCategory,
            List<OptimizeParams> candidates, int k) {
        if (k <= 0) {
            return List.of();
        }
        List<RankedParam> ranked = new ArrayList<>();
        for (OptimizeParams candidate : candidates) {
            DcaTakeProfitResult result = DcaTakeProfitSimulator.simulate(
                    navSequence, navDates, dcaAmount, candidate.toTakeProfitParams(), fundCategory);
            BigDecimal ret = result.strategyReturn();
            BigDecimal mdd = MaxDrawdownCalculator.calculate(result.dailyValues());
            BigDecimal calmar = BenchmarkCalculator.calmarRatio(ret, mdd); // 回撤0→null(+∞)
            ranked.add(new RankedParam(candidate, calmar, ret, mdd));
        }
        // null(Calmar +∞)优先,否则数值降序
        ranked.sort(Comparator.comparing(RankedParam::trainCalmar,
                Comparator.nullsFirst(Comparator.reverseOrder())));
        return ranked.size() <= k ? ranked : ranked.subList(0, k);
    }
}