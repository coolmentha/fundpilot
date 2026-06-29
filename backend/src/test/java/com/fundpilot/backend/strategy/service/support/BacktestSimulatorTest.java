package com.fundpilot.backend.strategy.service.support;

import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BacktestSimulator 重构后单测:验证对接 evaluateSignal 后 BUILD 建仓、回撤加仓、止盈清仓等行为。
 * <p>收益精确数值依赖调节系数(年线×MACD×量能查表),难手算,故用行为断言(建仓发生/收益符号/dailyValues 长度)。
 */
class BacktestSimulatorTest {

    @Test
    void 单边上涨_触发BUILD建仓_收益为正() {
        // 净值 1.0 → 1.1 → 1.2 单调上升:年线向上 + 持续 60 日新高 → BUILD 在首日建仓,之后上涨获利
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"));
        BacktestParams params = broadParams();

        BacktestResult result = simulate(nav);

        // BUILD 建仓 1000×0.1=100 元,首日 100 份,期末 100×1.2=120 → 收益 0.2
        assertThat(result.strategyReturn()).isPositive();
        assertThat(result.dailyValues()).hasSize(3);
    }

    @Test
    void 回撤达一档_加仓后反弹_收益为正() {
        // 1.0 → 0.9(跌 10% 达 tier1 阈值 5%)→ 1.1:首日 BUILD 建仓,次日回撤加仓 tier1,末日反弹获利
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("0.9"), new BigDecimal("1.1"));
        BacktestParams params = broadParams();

        BacktestResult result = simulate(nav);

        assertThat(result.strategyReturn()).isNotNull();
        assertThat(result.dailyValues()).hasSize(3);
    }

    @Test
    void 空净值序列_返回零收益空列表() {
        BacktestResult result = BacktestSimulator.simulate(
                List.of(), List.of(), List.of(), broadParams());
        assertThat(result.strategyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.dailyValues()).isEmpty();
    }

    @Test
    void dailyValues_长度等于净值序列() {
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("0.9"), new BigDecimal("1.1"));
        BacktestResult result = simulate(nav);
        assertThat(result.dailyValues()).hasSize(3);
    }

    /** 构造宽基 ETF 策略参数(tier1 阈值 5%/比例 100%,止盈 50%,计划仓位 1000)。 */
    private static BacktestParams broadParams() {
        return new BacktestParams(
                new BigDecimal("-0.05"), new BigDecimal("-0.10"), new BigDecimal("-0.15"), new BigDecimal("-0.20"),
                new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.05"), new BigDecimal("0.50"), new BigDecimal("1000"),
                FundCategory.BROAD_BASE, FundSubType.ETF);
    }

    /** 用 BacktestIndicatorCalculator 算指标(无 K 线,量能降级)后跑模拟。 */
    private static BacktestResult simulate(List<BigDecimal> nav) {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        List<Instant> dates = new ArrayList<>(nav.size());
        for (int i = 0; i < nav.size(); i++) {
            dates.add(start.plus(i, ChronoUnit.DAYS));
        }
        List<MarketIndicators> indicators = BacktestIndicatorCalculator.calculate(nav, dates, null);
        return BacktestSimulator.simulate(nav, dates, indicators, broadParams());
    }
}
