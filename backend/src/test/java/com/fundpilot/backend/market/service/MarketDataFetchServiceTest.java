package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.repository.MarketIndicatorSnapshotRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #7 循环 F:{@code MarketDataFetchService.fetchBatch} 编排逻辑。
 * 验收:3 只基金批量落库 / 单只失败其他继续 / refreshAll 全量覆盖。
 */
@Import(MarketDataFetchServiceTest.MockEastmoneyConfig.class)
class MarketDataFetchServiceTest extends AbstractIntegrationTest {

    @MockitoBean
    MarketDataSource marketDataSource;

    @Autowired
    MarketDataFetchService marketDataFetchService;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundStrategyRepository fundStrategyRepository;

    @Autowired
    MarketIndicatorSnapshotRepository snapshotRepository;

    @Autowired
    ApplicationContext applicationContext;

    @Test
    @Transactional
    void refreshAll_三只基金全部落库_snapshot_表_3_行() {
        FundEntity f1 = persistEffectiveFund("161725", "1.000300");
        FundEntity f2 = persistEffectiveFund("161726", "1.000300");
        FundEntity f3 = persistEffectiveFund("161727", "1.000300");
        mockNavHistory();
        mockIndexKline();

        marketDataFetchService.refreshAll();

        long count = snapshotRepository.count();
        assertThat(count).isEqualTo(3L);
        assertThat(snapshotRepository.findByFundEntity_IdAndSnapshotDate(f1.getId(), Instant.now())).isPresent();
    }

    @Test
    @Transactional
    void fetchBatch_单只基金拉取抛异常_其他基金继续落库() {
        FundEntity ok1 = persistEffectiveFund("161728", "1.000300");
        FundEntity bad = persistEffectiveFund("161729", "1.000300");
        FundEntity ok2 = persistEffectiveFund("161730", "1.000300");
        // bad 基金拉净值时抛异常
        when(marketDataSource.fetchNavHistory("161729"))
                .thenThrow(new RuntimeException("东方财富接口超时"));
        when(marketDataSource.fetchNavHistory("161728")).thenReturn(sampleNavHistory());
        when(marketDataSource.fetchNavHistory("161730")).thenReturn(sampleNavHistory());
        mockIndexKline();

        marketDataFetchService.refreshAll();

        // bad 基金不落库,其余两只正常
        assertThat(snapshotRepository.findByFundEntity_IdAndSnapshotDate(bad.getId(), Instant.now())).isEmpty();
        assertThat(snapshotRepository.findByFundEntity_IdAndSnapshotDate(ok1.getId(), Instant.now())).isPresent();
        assertThat(snapshotRepository.findByFundEntity_IdAndSnapshotDate(ok2.getId(), Instant.now())).isPresent();
    }

    @Test
    @Transactional
    void refreshAll_同日重跑_不新增行_幂等覆盖() {
        persistEffectiveFund("161731", "1.000300");
        mockNavHistory();
        mockIndexKline();

        marketDataFetchService.refreshAll();
        long firstRun = snapshotRepository.count();

        marketDataFetchService.refreshAll();
        long secondRun = snapshotRepository.count();

        assertThat(secondRun).isEqualTo(firstRun);
    }

    private void mockNavHistory() {
        when(marketDataSource.fetchNavHistory(anyString())).thenReturn(sampleNavHistory());
    }

    private void mockIndexKline() {
        when(marketDataSource.fetchIndexKline(anyString(), anyString())).thenReturn(sampleIndexKline());
    }

    private static List<FundNavSnapshot> sampleNavHistory() {
        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        return java.util.stream.IntStream.rangeClosed(0, 260)
                .mapToObj(i -> {
                    Instant d = start.plus(i, ChronoUnit.DAYS);
                    BigDecimal nav = new BigDecimal("1.00").add(new BigDecimal(i * 0.01 + ""));
                    return new FundNavSnapshot(d, nav, nav);
                })
                .toList();
    }

    private static IndexKline sampleIndexKline() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        List<IndexKline.Bar> bars = java.util.stream.IntStream.rangeClosed(0, 30)
                .mapToObj(i -> new IndexKline.Bar(
                        start.plus(i, ChronoUnit.DAYS),
                        new BigDecimal("100"),
                        new BigDecimal("101"),
                        new BigDecimal("102"),
                        new BigDecimal("99"),
                        1000L))
                .toList();
        return new IndexKline(bars);
    }

    private FundEntity persistEffectiveFund(String code, String benchmarkIndexCode) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName("测试基金-" + code);
        fund.setBenchmarkIndexCode(benchmarkIndexCode);
        fundRepository.save(fund);
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setFundEntity(fund);
        strategy.setStatus(StrategyParamStatus.EFFECTIVE);
        fundStrategyRepository.save(strategy);
        return fund;
    }

    @TestConfiguration
    static class MockEastmoneyConfig {
        // 占位:实际 mock 由 @MockBean 注入,本类仅声明便于后续扩展
    }
}
