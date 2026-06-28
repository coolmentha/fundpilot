package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.MarketDataSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #11 重构后:{@link DefaultStrategyBacktestService} 端到端回测编排 + passed 判定 + 窗口降级。
 * <p>沪深300 基准 + MarketDataSource(K线)用 @MockitoBean 注入避免真实网络;策略模拟对接 evaluateSignal。
 * tier 阈值用负数(领域约定:-0.05 表跌 5%,与 evaluateSignal 的 drawdown 负数口径一致)。
 */
class DefaultStrategyBacktestServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    Hs300BenchmarkProvider hs300BenchmarkProvider;

    @MockitoBean
    MarketDataSource marketDataSource;

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
    void run_固定净值序列_落库回测结果含策略收益与基准() {
        FundEntity fund = persistBroadFund(new BigDecimal("1000"));
        // 净值 1.0 → 0.9(跌10%超tier1 -5%)→ 1.1
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0");
        persistNav(fund, Instant.parse("2025-01-20T00:00:00Z"), "0.9");
        persistNav(fund, Instant.parse("2025-02-10T00:00:00Z"), "1.1");
        Long strategyId = persistStrategy(fund);
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        // 策略收益非 null(对接 evaluateSignal 后 BUILD/ADD 触发,具体值依赖系数,断言符号与边界)
        assertThat(result.getStrategyReturn()).isNotNull();
        // all-in:1.1/1.0-1=0.1;回撤 (1.0-0.9)/1.0=0.1
        assertThat(result.getBenchmarkAllInReturn()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.001")));
        assertThat(result.getBenchmarkAllInMaxDrawdown()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.01")));
    }

    @Test
    @Transactional
    void run_上涨行情触发BUILD_策略收益为正() {
        FundEntity fund = persistBroadFund(new BigDecimal("1000"));
        // 65 期递增:年线递增 + 持续 60 日新高 → BUILD 触发建仓,上涨后收益为正(修复原"漏建仓收益0"问题)
        Instant base = Instant.parse("2025-01-01T00:00:00Z");
        for (int i = 0; i < 65; i++) {
            persistNav(fund, base.plus(i, ChronoUnit.DAYS), BigDecimal.valueOf(1.0 + i * 0.01).toPlainString());
        }
        Long strategyId = persistStrategy(fund);
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        // 核心:单边上涨行情 BUILD 建仓后,策略收益为正(不再是 0)
        assertThat(result.getStrategyReturn()).as("strategyReturn=%s", result.getStrategyReturn()).isPositive();
    }

    @Test
    @Transactional
    void run_策略收益跑输沪深300_passed_false() {
        FundEntity fund = persistBroadFund(new BigDecimal("1000"));
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0");
        persistNav(fund, Instant.parse("2025-01-20T00:00:00Z"), "0.9");
        persistNav(fund, Instant.parse("2025-02-10T00:00:00Z"), "1.1");
        Long strategyId = persistStrategy(fund);
        // mock hs300 收益 5.0(远超策略)→ passed=false
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("5.0"), new BigDecimal("0.10")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        assertThat(result.isPassed()).isFalse();
    }

    @Test
    @Transactional
    void run_窗口降级_基金不满一年_start取最早净值日期() {
        FundEntity fund = persistBroadFund(new BigDecimal("1000"));
        Instant earliest = Instant.now().minus(90, ChronoUnit.DAYS);
        persistNav(fund, earliest, "1.0");
        persistNav(fund, earliest.plus(10, ChronoUnit.DAYS), "0.9");
        persistNav(fund, earliest.plus(20, ChronoUnit.DAYS), "1.1");
        Long strategyId = persistStrategy(fund);
        when(hs300BenchmarkProvider.fetch(any(), any()))
                .thenReturn(new BenchmarkMetrics(new BigDecimal("0.05"), new BigDecimal("0.10")));
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        BacktestWindow window = new BacktestWindow(
                Instant.now().minus(365, ChronoUnit.DAYS), Instant.now());
        StrategyBacktestEntity result = backtestService.run(strategyId, window);

        assertThat(result.getBacktestStartDate().getEpochSecond())
                .isCloseTo(earliest.getEpochSecond(), within(1000L));
    }

    @Test
    @Transactional
    void run_净值不足两点_落零指标_passed_false() {
        FundEntity fund = persistBroadFund(new BigDecimal("1000"));
        persistNav(fund, Instant.parse("2025-01-10T00:00:00Z"), "1.0");
        Long strategyId = persistStrategy(fund);
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(null);

        StrategyBacktestEntity result = backtestService.run(strategyId, window());

        assertThat(result.getStrategyReturn()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.isPassed()).isFalse();
    }

    private BacktestWindow window() {
        return new BacktestWindow(
                Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-12-31T00:00:00Z"));
    }

    private FundEntity persistBroadFund(BigDecimal plannedTotalAmount) {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setFundSubType(FundSubType.ETF);
        fund.setPlannedTotalAmount(plannedTotalAmount);
        return fundRepository.save(fund);
    }

    private void persistNav(FundEntity fund, Instant date, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(date);
        entity.setNav(new BigDecimal(accumulatedNav));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        fundNavHistoryRepository.save(entity);
    }

    private Long persistStrategy(FundEntity fund) {
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        // 阈值用负数(领域约定:-0.05 表跌 5%)
        strategy.setTier1Drawdown(new BigDecimal("-0.05"));
        strategy.setTier2Drawdown(new BigDecimal("-0.10"));
        strategy.setTier3Drawdown(new BigDecimal("-0.15"));
        strategy.setTier4Drawdown(new BigDecimal("-0.20"));
        strategy.setTier1Ratio(new BigDecimal("1.0"));
        strategy.setTier2Ratio(BigDecimal.ZERO);
        strategy.setTier3Ratio(BigDecimal.ZERO);
        strategy.setTier4Ratio(BigDecimal.ZERO);
        strategy.setWeeklyCoolDownThreshold(new BigDecimal("0.05"));
        strategy.setStopLossPullbackPercent(new BigDecimal("0.50"));
        return fundStrategyRepository.save(strategy).getId();
    }
}
