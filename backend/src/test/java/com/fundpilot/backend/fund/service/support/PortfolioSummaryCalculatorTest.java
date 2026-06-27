package com.fundpilot.backend.fund.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #18 组合盈亏聚合纯函数单测(CONTEXT.md「概览页盈亏 KPI」)。
 * <p>故事 24 核心:上涨/下跌(按今日涨跌幅符号)与盈利/亏损(按总盈亏符号)是两个独立维度——
 * 一只基金可能今日上涨但整体亏损,两者独立计数不混用。
 */
class PortfolioSummaryCalculatorTest {

    @Test
    void 聚合_今日盈亏合计与涨跌盈亏计数() {
        // 三只基金:今日盈亏 +60 / -20 / +30 = 合计 +70
        // 涨跌:+0.05(涨) / -0.02(跌) / +0.03(涨) → 上涨2 下跌1
        // 总盈亏:+300(盈) / -200(亏) / +100(盈) → 盈利2 亏损1
        List<BigDecimal> changePcts = list("0.05", "-0.02", "0.03");
        List<BigDecimal> dailyPnls = list("60", "-20", "30");
        List<BigDecimal> totalPnls = list("300", "-200", "100");

        PortfolioSummary summary = FundPnlCalculator.summarize(changePcts, dailyPnls, totalPnls);

        assertThat(summary.dailyPnlTotal()).isCloseTo(new BigDecimal("70"), within(new BigDecimal("0.01")));
        assertThat(summary.risingFundCount()).isEqualTo(2);
        assertThat(summary.fallingFundCount()).isEqualTo(1);
        assertThat(summary.profitableFundCount()).isEqualTo(2);
        assertThat(summary.losingFundCount()).isEqualTo(1);
    }

    @Test
    void story24_今日上涨但整体亏损_两个维度独立归类() {
        // 一只基金:今日涨跌 +0.04(上涨) 但总盈亏 -150(亏损)
        // → 既计入上涨 又计入亏损,两维度独立不混用
        List<BigDecimal> changePcts = list("0.04");
        List<BigDecimal> dailyPnls = list("40");
        List<BigDecimal> totalPnls = list("-150");

        PortfolioSummary summary = FundPnlCalculator.summarize(changePcts, dailyPnls, totalPnls);

        assertThat(summary.risingFundCount()).isEqualTo(1);
        assertThat(summary.fallingFundCount()).isZero();
        assertThat(summary.profitableFundCount()).isZero();
        assertThat(summary.losingFundCount()).isEqualTo(1);
    }

    @Test
    void 聚合_null指标跳过_不影响其它基金计数() {
        // 第二只基金无涨跌/盈亏数据(null),但仍计入合计为 0
        List<BigDecimal> changePcts = list("0.05", null);
        List<BigDecimal> dailyPnls = list("60", null);
        List<BigDecimal> totalPnls = list("300", null);

        PortfolioSummary summary = FundPnlCalculator.summarize(changePcts, dailyPnls, totalPnls);

        assertThat(summary.dailyPnlTotal()).isCloseTo(new BigDecimal("60"), within(new BigDecimal("0.01")));
        assertThat(summary.risingFundCount()).isEqualTo(1);
        assertThat(summary.profitableFundCount()).isEqualTo(1);
    }

    @Test
    void 聚合_空列表_全部归零() {
        PortfolioSummary summary = FundPnlCalculator.summarize(List.of(), List.of(), List.of());

        assertThat(summary.dailyPnlTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.risingFundCount()).isZero();
        assertThat(summary.fallingFundCount()).isZero();
        assertThat(summary.profitableFundCount()).isZero();
        assertThat(summary.losingFundCount()).isZero();
    }

    private List<BigDecimal> list(String... vals) {
        return java.util.Arrays.stream(vals).map(v -> v == null ? null : new BigDecimal(v)).toList();
    }
}
