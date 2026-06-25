package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.repository.StrategyBacktestRepository;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * issue #11 循环 B-3:{@link DefaultStrategyBacktestService} 端到端回测编排 + passed 判定 + 窗口降级。
 * <p>沪深300 基准线用 {@code @MockitoBean} 注入,避免真实网络;其余 all-in/DCA/策略模拟均为真实计算。
 */
class DefaultStrategyBacktestServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    Hs300BenchmarkProvider hs300BenchmarkProvider;

    @Autowired
    DefaultStrategyBacktestService backtestService;

    @Autowired
    FundStrategyRepository fundStrategyRepository;

    @Autowired
    StrategyBacktestRepository strategyBacktestRepository;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundNavHistoryRepository fundNavHistoryRepository;

    @Test
    @Transactional
    void run_固定净值序列_策略收益与基准数学正确() {
        FundEntity fund = persistFundWithAmount(new BigDecimal("1000"));
        // 净值 1.0 → 0.9(跌10%达tier1阈值5%,买入)→ 1.1
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0", "1.0");
        persistNav(fund, Instant.parse("2025-01-20T00:00:00Z"), "0.9", "0.9");
        persistNav(fund, Instant.parse("2025-02-10T00:00:00Z"), "1.1", "1.1");
        Long strategyId = persistStrategy(fund);
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        // 策略:day1 买 1000/0.9=1111.11 份,期末 1111.11*1.1=1222.22,收益 0.2222;单调上升回撤 0
        assertThat(result.getStrategyReturn()).isCloseTo(new BigDecimal("0.2222"), within(new BigDecimal("0.01")));
        assertThat(result.getStrategyMaxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
        // all-in:1.1/1.0-1=0.1;回撤 (1.0-0.9)/1.0=0.1(峰值1.0跌到0.9)
        assertThat(result.getBenchmarkAllInReturn()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.001")));
        assertThat(result.getBenchmarkAllInMaxDrawdown()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.01")));
    }

    @Test
    @Transactional
    void run_收益跑赢三条且回撤不超allIn_passed_true() {
        FundEntity fund = persistFundWithAmount(new BigDecimal("1000"));
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0", "1.0");
        persistNav(fund, Instant.parse("2025-01-20T00:00:00Z"), "0.9", "0.9");
        persistNav(fund, Instant.parse("2025-02-10T00:00:00Z"), "1.1", "1.1");
        Long strategyId = persistStrategy(fund);
        // 策略收益 0.2222 > hs300 0.05 / all-in 0.1 / dca 0.1111;策略回撤 0 <= all-in 回撤 0.1818
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @Transactional
    void run_策略收益跑输沪深300_passed_false() {
        FundEntity fund = persistFundWithAmount(new BigDecimal("1000"));
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0", "1.0");
        persistNav(fund, Instant.parse("2025-01-20T00:00:00Z"), "0.9", "0.9");
        persistNav(fund, Instant.parse("2025-02-10T00:00:00Z"), "1.1", "1.1");
        Long strategyId = persistStrategy(fund);
        // 策略收益约 0.2222;mock hs300 收益 0.30(策略跑输沪深300)→ passed=false
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.30"), new BigDecimal("0.10")));

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        assertThat(result.isPassed()).isFalse();
    }

    @Test
    @Transactional
    void run_收益跑赢三条但回撤等于allIn_passed_true_leq边界() {
        // 策略满仓持有过峰值后大跌(不止盈),策略回撤==all-in 回撤(同份额同期净值回撤);
        // V 型反转使策略收益跑赢三条 → 验证 passed 回撤条件用 <= (临界通过)。
        // 1.0 → 0.9(买入)→ 1.3(涨)
        FundEntity fund = persistFundWithAmount(new BigDecimal("1000"));
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0", "1.0");
        persistNav(fund, Instant.parse("2025-01-20T00:00:00Z"), "0.9", "0.9");
        persistNav(fund, Instant.parse("2025-02-10T00:00:00Z"), "1.3", "1.3");
        Long strategyId = persistStrategy(fund);
        // 策略收益 0.4444;all-in 收益 0.3 回撤 0.1818;策略回撤 0(单调) <= 0.1818
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        assertThat(result.isPassed()).isTrue();
    }

    @Test
    @Transactional
    void run_窗口降级_基金不满一年_start取最早净值日期() {
        FundEntity fund = persistFundWithAmount(new BigDecimal("1000"));
        // 净值最早 3 个月前,但 window.start 是 1 年前
        Instant earliest = Instant.now().minus(90, ChronoUnit.DAYS);
        persistNav(fund, earliest, "1.0", "1.0");
        persistNav(fund, earliest.plus(10, ChronoUnit.DAYS), "0.9", "0.9");
        persistNav(fund, earliest.plus(20, ChronoUnit.DAYS), "1.1", "1.1");
        Long strategyId = persistStrategy(fund);
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));

        BacktestWindow window = new BacktestWindow(
                Instant.now().minus(365, ChronoUnit.DAYS), Instant.now());
        StrategyBacktestEntity result = backtestService.run(strategyId, window);

        // backtestStartDate 降级为最早净值日期(约90天前),而非365天前
        assertThat(result.getBacktestStartDate().getEpochSecond())
                .isCloseTo(earliest.getEpochSecond(), within(1000L));
    }

    @Test
    @Transactional
    void run_净值不足两点_落零指标_passed_false() {
        FundEntity fund = persistFundWithAmount(new BigDecimal("1000"));
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0", "1.0");
        Long strategyId = persistStrategy(fund);

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        assertThat(result.getStrategyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.isPassed()).isFalse();
    }

    private BacktestWindow window() {
        return new BacktestWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T00:00:00Z"));
    }

    private FundEntity persistFundWithAmount(BigDecimal plannedTotalAmount) {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        fund.setPlannedTotalAmount(plannedTotalAmount);
        return fundRepository.save(fund);
    }

    private void persistNav(FundEntity fund, Instant date, String nav, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(date);
        entity.setNav(new BigDecimal(nav));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        fundNavHistoryRepository.save(entity);
    }

    private Long persistStrategy(FundEntity fund) {
        return persistStrategyWithStopLoss(fund, new BigDecimal("0.50"));
    }

    private Long persistStrategyWithStopLoss(FundEntity fund, BigDecimal stopLossPullback) {
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        strategy.setTier1Drawdown(new BigDecimal("0.05"));
        strategy.setTier2Drawdown(new BigDecimal("0.10"));
        strategy.setTier3Drawdown(new BigDecimal("0.15"));
        strategy.setTier4Drawdown(new BigDecimal("0.20"));
        strategy.setTier1Ratio(new BigDecimal("1.0"));
        strategy.setTier2Ratio(BigDecimal.ZERO);
        strategy.setTier3Ratio(BigDecimal.ZERO);
        strategy.setTier4Ratio(BigDecimal.ZERO);
        strategy.setWeeklyCoolDownThreshold(new BigDecimal("0.05"));
        strategy.setStopLossPullbackPercent(stopLossPullback);
        return fundStrategyRepository.save(strategy).getId();
    }
}
