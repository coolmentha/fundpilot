package com.fundpilot.backend.strategy.service.support;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class BacktestSimulatorTest {

    @Test
    void 跌幅达一档_加仓后反弹_收益为正() {
        // 净值 1.0 → 0.9(跌 10%,达 tier1 阈值 5%)→ 1.1
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("0.9"), new BigDecimal("1.1"));
        BacktestParams params = new BacktestParams(
                new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.15"), new BigDecimal("0.20"),
                new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.05"), new BigDecimal("0.50"), new BigDecimal("1000"));

        BacktestResult result = BacktestSimulator.simulate(nav, params);

        // day1 买 1000 份额 1000/0.9=1111.11;期末 1111.11*1.1=1222.22;return=(1222.22-1000)/1000=0.2222
        assertThat(result.strategyReturn()).isCloseTo(new BigDecimal("0.2222"), within(new BigDecimal("0.01")));
    }

    @Test
    void 无跌幅_不建仓_收益为_0() {
        // 单调上升,永不触发加仓
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("1.1"), new BigDecimal("1.2"));
        BacktestParams params = new BacktestParams(
                new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.15"), new BigDecimal("0.20"),
                new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.05"), new BigDecimal("0.50"), new BigDecimal("1000"));

        BacktestResult result = BacktestSimulator.simulate(nav, params);

        assertThat(result.strategyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 持仓期峰值回落超止盈阈值_全部卖出() {
        // 1.0 → 0.9(买入)→ 1.2(峰值)→ 0.6(回落 50%,达止盈 0.5)→ 0.6(已空仓)
        List<BigDecimal> nav = List.of(
                new BigDecimal("1.0"), new BigDecimal("0.9"), new BigDecimal("1.2"),
                new BigDecimal("0.6"), new BigDecimal("0.6"));
        BacktestParams params = new BacktestParams(
                new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.15"), new BigDecimal("0.20"),
                new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.05"), new BigDecimal("0.50"), new BigDecimal("1000"));

        BacktestResult result = BacktestSimulator.simulate(nav, params);

        // day1 买 1000/0.9=1111.11 份;day2 峰值市值 1111.11*1.2=1333.33;
        // day3 市值 1111.11*0.6=666.67,回落 (1333.33-666.67)/1333.33=0.5 >= 0.5 → 止盈卖出,得 666.67
        // 期末市值=666.67,投入=1000,return=(666.67-1000)/1000=-0.3333
        assertThat(result.strategyReturn()).isCloseTo(new BigDecimal("-0.3333"), within(new BigDecimal("0.01")));
    }

    @Test
    void dailyValues_长度等于净值序列() {
        List<BigDecimal> nav = List.of(new BigDecimal("1.0"), new BigDecimal("0.9"), new BigDecimal("1.1"));
        BacktestParams params = new BacktestParams(
                new BigDecimal("0.05"), new BigDecimal("0.10"), new BigDecimal("0.15"), new BigDecimal("0.20"),
                new BigDecimal("1.0"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("0.05"), new BigDecimal("0.50"), new BigDecimal("1000"));

        BacktestResult result = BacktestSimulator.simulate(nav, params);

        assertThat(result.dailyValues()).hasSize(3);
    }
}
