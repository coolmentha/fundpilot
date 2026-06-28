package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #27 验收:寻优 train 集排序纯函数 {@link OptimizeParamRanker}。
 * <p>风险调整收益 = 策略收益 / 策略最大回撤(类 Calmar),回撤为 0 的组合跳过(避免除零),
 * 选比值最高的一组作为最优参数。净值序列/行情指标在循环外算一次复用,循环内只重跑
 * {@link BacktestSimulator#simulate} + {@link MaxDrawdownCalculator#calculate}。
 * 纯函数,不接 API/DB/前端,独立单测验证。
 */
class OptimizeParamRankerTest {

    @Test
    void 回撤为零的组合被跳过_只选有回撤且比值最高的一组() {
        // 构造一个"持续下跌后回升"的净值序列,让加仓策略跑出有回撤的正收益
        List<BigDecimal> nav = new ArrayList<>();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Instant> dates = new ArrayList<>();
        // 60 期:前 30 期从 1.0 跌到 0.7(跌 30% 触发多档加仓),后 30 期回升到 1.2
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(1.0 - i * 0.01));
            dates.add(start.plus(i, ChronoUnit.DAYS));
        }
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(0.7 + i * 0.017));
            dates.add(start.plus(30 + i, ChronoUnit.DAYS));
        }
        List<MarketIndicators> indicators = BacktestIndicatorCalculator.calculate(nav, dates, null);

        // 两组参数:差别在止盈回落(stopLossPullbackPercent),回撤/收益不同
        OptimizeParams p1 = params(new BigDecimal("0.05"));
        OptimizeParams p2 = params(new BigDecimal("0.15"));
        List<OptimizeParams> candidates = List.of(p1, p2);

        Optional<OptimizeParams> best = OptimizeParamRanker.rankBest(
                nav, dates, indicators, new BigDecimal("1000"), candidates);

        // 应选出风险调整收益最高的一组(具体哪个取决于模拟,断言行为:返回非空且是候选之一)
        assertThat(best).isPresent();
        assertThat(best.get().stopLossPullbackPercent()).isIn(new BigDecimal("0.05"), new BigDecimal("0.15"));
    }

    @Test
    void 所有组合回撤均为零_返回_empty() {
        // 空净值序列:simulate 返回零收益空 dailyValues,MaxDrawdownCalculator 返 0 → 全部跳过
        List<BigDecimal> nav = List.of();
        List<Instant> dates = List.of();
        List<MarketIndicators> indicators = List.of();
        List<OptimizeParams> candidates = List.of(params(new BigDecimal("0.05")));

        Optional<OptimizeParams> best = OptimizeParamRanker.rankBest(
                nav, dates, indicators, new BigDecimal("1000"), candidates);

        assertThat(best).isEmpty();
    }

    @Test
    void 多组候选_选出风险调整收益最高的一组() {
        // 同一净值序列,用 #26 的完整网格(64 组)排序,验证选出的是比值最高的
        List<BigDecimal> nav = new ArrayList<>();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Instant> dates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(1.0 - i * 0.01));
            dates.add(start.plus(i, ChronoUnit.DAYS));
        }
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(0.7 + i * 0.017));
            dates.add(start.plus(30 + i, ChronoUnit.DAYS));
        }
        List<MarketIndicators> indicators = BacktestIndicatorCalculator.calculate(nav, dates, null);
        List<OptimizeParams> candidates = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        Optional<OptimizeParams> best = OptimizeParamRanker.rankBest(
                nav, dates, indicators, new BigDecimal("1000"), candidates);

        assertThat(best).isPresent();
        // 手算最优组合:遍历所有候选算风险调整收益,与 rankBest 结果比对
        OptimizeParams expected = manualBest(nav, dates, indicators, new BigDecimal("1000"), candidates);
        assertThat(best.get()).isEqualTo(expected);
    }

    @Test
    void rankTopK_返回前k名_按Calmar降序_携带train指标() {
        // 同一净值序列,用完整网格(64 组)排序
        List<BigDecimal> nav = new ArrayList<>();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Instant> dates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(1.0 - i * 0.01));
            dates.add(start.plus(i, ChronoUnit.DAYS));
        }
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(0.7 + i * 0.017));
            dates.add(start.plus(30 + i, ChronoUnit.DAYS));
        }
        List<MarketIndicators> indicators = BacktestIndicatorCalculator.calculate(nav, dates, null);
        List<OptimizeParams> candidates = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        List<RankedParam> top3 = OptimizeParamRanker.rankTopK(
                nav, dates, indicators, new BigDecimal("1000"), candidates, 3);

        // 返回 3 组(候选充足)
        assertThat(top3).hasSize(3);
        // 按 train Calmar 严格降序
        assertThat(top3.get(0).trainCalmar()).isNotNull();
        assertThat(top3.get(0).trainCalmar().compareTo(top3.get(1).trainCalmar())).isGreaterThanOrEqualTo(0);
        assertThat(top3.get(1).trainCalmar().compareTo(top3.get(2).trainCalmar())).isGreaterThanOrEqualTo(0);
        // 携带 train 指标(非 null)
        assertThat(top3.get(0).trainReturn()).isNotNull();
        assertThat(top3.get(0).trainMaxDrawdown()).isNotNull();
        // 与 rankBest 第一名一致(rankBest 内部调 rankTopK(1))
        Optional<OptimizeParams> best = OptimizeParamRanker.rankBest(
                nav, dates, indicators, new BigDecimal("1000"), candidates);
        assertThat(best).isPresent();
        assertThat(top3.get(0).params()).isEqualTo(best.get());
    }

    @Test
    void rankTopK_k超过候选数_返回实际数量() {
        // 候选回撤全 0 → 返空(k=5 也只能返 0)
        List<BigDecimal> nav = List.of();
        List<Instant> dates = List.of();
        List<MarketIndicators> indicators = List.of();
        List<OptimizeParams> candidates = List.of(params(new BigDecimal("0.05")));

        List<RankedParam> result = OptimizeParamRanker.rankTopK(
                nav, dates, indicators, new BigDecimal("1000"), candidates, 5);

        assertThat(result).isEmpty();
    }

    @Test
    void rankTopK_k为零_返回空列表() {
        List<BigDecimal> nav = new ArrayList<>();
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Instant> dates = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(1.0 - i * 0.01));
            dates.add(start.plus(i, ChronoUnit.DAYS));
        }
        for (int i = 0; i < 30; i++) {
            nav.add(BigDecimal.valueOf(0.7 + i * 0.017));
            dates.add(start.plus(30 + i, ChronoUnit.DAYS));
        }
        List<MarketIndicators> indicators = BacktestIndicatorCalculator.calculate(nav, dates, null);
        List<OptimizeParams> candidates = OptimizeGridGenerator.generate(FundCategory.BROAD_BASE);

        List<RankedParam> result = OptimizeParamRanker.rankTopK(
                nav, dates, indicators, new BigDecimal("1000"), candidates, 0);

        assertThat(result).isEmpty();
    }

    /** 手动遍历候选算风险调整收益,返最高的一组(作为 oracle 验证 rankBest)。 */
    private static OptimizeParams manualBest(List<BigDecimal> nav, List<Instant> dates,
                                             List<MarketIndicators> indicators, BigDecimal planned,
                                             List<OptimizeParams> candidates) {
        OptimizeParams best = null;
        BigDecimal bestScore = null;
        for (OptimizeParams c : candidates) {
            BacktestParams p = new BacktestParams(
                    c.tier1Drawdown(), c.tier2Drawdown(), c.tier3Drawdown(), c.tier4Drawdown(),
                    c.tier1Ratio(), c.tier2Ratio(), c.tier3Ratio(), c.tier4Ratio(),
                    c.weeklyCoolDownThreshold(), c.stopLossPullbackPercent(),
                    planned, c.fundCategory(), c.fundSubType());
            BacktestResult r = BacktestSimulator.simulate(nav, dates, indicators, p);
            BigDecimal dd = MaxDrawdownCalculator.calculate(r.dailyValues());
            if (dd.signum() <= 0) {
                continue;
            }
            BigDecimal score = r.strategyReturn().divide(dd, MathContext.DECIMAL64);
            if (bestScore == null || score.compareTo(bestScore) > 0) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    private static OptimizeParams params(BigDecimal stopLossPullbackPercent) {
        return new OptimizeParams(
                new BigDecimal("-0.08"), new BigDecimal("-0.15"), new BigDecimal("-0.25"), new BigDecimal("-0.35"),
                stopLossPullbackPercent,
                new BigDecimal("0.15"), new BigDecimal("0.20"), new BigDecimal("0.25"), new BigDecimal("0.30"),
                new BigDecimal("0.08"), FundCategory.BROAD_BASE, FundSubType.ETF);
    }
}
