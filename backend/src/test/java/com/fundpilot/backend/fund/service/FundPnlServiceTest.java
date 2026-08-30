package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.PortfolioSummary;
import com.fundpilot.backend.marketdata.adapter.api.realtimevaluation.MarketEstimateApi;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.MarketRealtimeRedisStore;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * issue #18 盈亏/涨跌多表聚合集成测试(工作台领域上下文「今日涨跌/今日盈亏/总盈亏」)。
 * <p>落 fund_nav_history 最近两期累计净值 + CONFIRMED 交易,验证 FundPnlService 聚合。
 * 算术委托 {@link com.fundpilot.backend.fund.service.support.FundPnlCalculator},本类只验多表拼装。
 */
class FundPnlServiceTest extends AbstractIntegrationTest {

    /** issue #38:mock 实时估值契约为空,让三态降级到落库净值算(隔离网络,恢复原数值断言)。 */
    @MockitoBean
    MarketEstimateApi marketEstimates;

    @MockitoBean
    Clock clock;

    @MockitoBean
    MarketRealtimeRedisStore marketRealtimeRedisStore;

    @Autowired FundPnlService fundPnlService;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired FundNavHistoryRepository fundNavHistoryRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(marketEstimates.getEstimates(anyList())).thenReturn(Map.of());
        when(marketEstimates.getEstimateStatus(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(MarketEstimateApi.Status.NOT_ATTEMPTED);
        when(clock.instant()).thenReturn(Instant.parse("2026-07-06T03:30:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(clock.withZone(ZoneOffset.UTC)).thenReturn(clock);
        // 清理可能残留的旧数据
        fundTransactionRepository.deleteAll();
        fundNavHistoryRepository.deleteAll();
        fundRepository.deleteAll();
    }

    @Test
    @Transactional
    void 持仓基金_聚合今日涨跌今日盈亏总盈亏() {
        FundEntity fund = persistHoldingFund();
        // 累计净值 1.20 → 1.26(涨 5%);持仓 1000 份;成本单价 1.20;总盈亏 = 1000×(1.26-1.20) = 60
        // 最近一期净值日期 = 今天,触发"盘后态"(todayNavConfirmed=true),
        // 这样今日涨跌用落库净值算,不受当前时段(盘前/盘中)影响,测试随时跑都稳定。
        navHistory(fund, daysAgo(1), "1.20");
        navHistory(fund, daysAgo(0), "1.26");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1200", FundTransactionStatus.CONFIRMED);
        // 成本单价存在 FundEntity 上,不再从交易派生
        fund.setCostPerShare(new BigDecimal("1.20"));
        fundRepository.save(fund);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        assertThat(pnl.dailyChangePct()).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.0001")));
        assertThat(pnl.holdingShares()).isCloseTo(new BigDecimal("1000"), within(new BigDecimal("0.0001")));
        assertThat(pnl.holdingAmount()).isCloseTo(new BigDecimal("1260"), within(new BigDecimal("0.01")));
        assertThat(pnl.dailyPnl()).isCloseTo(new BigDecimal("60"), within(new BigDecimal("0.01")));
        assertThat(pnl.totalPnl()).isCloseTo(new BigDecimal("60"), within(new BigDecimal("0.01")));
    }

    @Test
    @Transactional
    void 单位净值与累计净值不同时_市值和总盈亏使用单位净值() {
        FundEntity fund = persistHoldingFund();
        navHistory(fund, daysAgo(1), "1.00", "2.00");
        navHistory(fund, daysAgo(0), "1.05", "2.10");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1000", FundTransactionStatus.CONFIRMED);
        fund.setCostPerShare(new BigDecimal("1.00"));
        fundRepository.save(fund);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        assertThat(pnl.dailyChangePct()).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.0001")));
        assertThat(pnl.holdingAmount()).isEqualByComparingTo("1050");
        assertThat(pnl.totalPnl()).isEqualByComparingTo("50");
    }

    @Test
    @Transactional
    void 无净值历史_盈亏字段为null() {
        FundEntity fund = persistHoldingFund();
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1200", FundTransactionStatus.CONFIRMED);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        // 无净值历史:持仓市值/盈亏类字段为 null(算不出);今日涨跌幅盘前态返 0、非盘前态返 null,
        // 不断言涨跌幅(依赖 CI 运行时段,见 DailyChangeResolver 三态判定)。
        assertThat(pnl.holdingAmount()).isNull();
        assertThat(pnl.dailyPnl()).isNull();
        assertThat(pnl.totalPnl()).isNull();
        // 持仓份额与成本不依赖净值,仍可算
        assertThat(pnl.holdingShares()).isCloseTo(new BigDecimal("1000"), within(new BigDecimal("0.0001")));
    }

    @Test
    @Transactional
    void 未建仓基金_有净值可看涨跌但持仓盈亏为null() {
        FundEntity fund = persistPendingFund();
        navHistory(fund, daysAgo(1), "1.20");
        navHistory(fund, daysAgo(0), "1.26");

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        // story 21:未建仓基金也能看今日涨跌
        assertThat(pnl.dailyChangePct()).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.0001")));
        // 无持仓:份额/市值/盈亏为 null
        assertThat(pnl.holdingShares()).isNull();
        assertThat(pnl.holdingAmount()).isNull();
        assertThat(pnl.dailyPnl()).isNull();
        assertThat(pnl.totalPnl()).isNull();
    }

    @Test
    @Transactional
    void 组合聚合_汇总所有持仓基金的今日盈亏合计与涨跌盈亏计数() {
        // 基金A:今日上涨 +5%(1.20→1.26),持仓1000份 成本单价1.20 → 今日盈亏+60 总盈亏+60(盈)
        // 最近一期净值 = 今天,触发盘后态,不受 CI 运行时段影响
        FundEntity fundA = persistHoldingFundWithCode("510300", "沪深300ETF");
        navHistory(fundA, daysAgo(1), "1.20");
        navHistory(fundA, daysAgo(0), "1.26");
        txWithAmount(fundA, FundTransactionSource.INCREASE, "1000", "1200", FundTransactionStatus.CONFIRMED);
        fundA.setCostPerShare(new BigDecimal("1.20"));
        fundRepository.save(fundA);

        // 基金B:今日下跌 -2%(1.00→0.98),持仓1000份 成本单价1.00 → 今日盈亏-20 总盈亏-20(亏)
        FundEntity fundB = persistHoldingFundWithCode("159825", "半导体ETF");
        navHistory(fundB, daysAgo(1), "1.00");
        navHistory(fundB, daysAgo(0), "0.98");
        txWithAmount(fundB, FundTransactionSource.INCREASE, "1000", "1000", FundTransactionStatus.CONFIRMED);
        fundB.setCostPerShare(new BigDecimal("1.00"));
        fundRepository.save(fundB);

        PortfolioSummary summary = fundPnlService.computePortfolioSummary();

        // 今日盈亏合计 = 60 + (-20) = 40
        assertThat(summary.dailyPnlTotal()).isCloseTo(new BigDecimal("40"), within(new BigDecimal("0.01")));
        assertThat(summary.risingFundCount()).isEqualTo(1);   // 基金A 上涨
        assertThat(summary.fallingFundCount()).isEqualTo(1);  // 基金B 下跌
        assertThat(summary.profitableFundCount()).isEqualTo(1); // 基金A 盈利
        assertThat(summary.losingFundCount()).isEqualTo(1);    // 基金B 亏损
    }

    @Test
    @Transactional
    void 组合聚合_盘中估值可用_使用fundgz涨跌幅并标记估算() {
        FundEntity fund = persistHoldingFundWithCode("008585", "华夏人工智能ETF联接A");
        navHistory(fund, Instant.parse("2026-07-02T00:00:00Z"), "1.84");
        navHistory(fund, Instant.parse("2026-07-03T00:00:00Z"), "1.8534");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1800", FundTransactionStatus.CONFIRMED);
        fund.setCostPerShare(new BigDecimal("1.80"));
        fundRepository.save(fund);
        when(marketEstimates.getEstimates(java.util.List.of("008585"))).thenReturn(Map.of(
                "008585", new MarketEstimateApi.Snapshot(new BigDecimal("0.0035"), "2026-07-06 11:30", "2026-07-03")));

        PortfolioSummary summary = fundPnlService.computePortfolioSummary();

        assertThat(summary.dailyPnlTotal()).isCloseTo(new BigDecimal("6.4869"), within(new BigDecimal("0.01")));
        assertThat(summary.risingFundCount()).isEqualTo(1);
        assertThat(summary.isEstimated()).isTrue();

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());
        assertThat(pnl.holdingAmount()).isCloseTo(new BigDecimal("1859.8869"), within(new BigDecimal("0.01")));
        assertThat(pnl.totalPnl()).isCloseTo(new BigDecimal("59.8869"), within(new BigDecimal("0.01")));
    }

    @Test
    @Transactional
    void 组合聚合_估值拉取失败_总仓位使用最近确认净值且今日收益未知() {
        FundEntity fund = persistHoldingFundWithCode("008585", "华夏人工智能ETF联接A");
        navHistory(fund, Instant.parse("2026-07-02T00:00:00Z"), "1.84");
        navHistory(fund, Instant.parse("2026-07-03T00:00:00Z"), "1.8534");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1800", FundTransactionStatus.CONFIRMED);
        fund.setCostPerShare(new BigDecimal("1.80"));
        fundRepository.save(fund);
        when(marketEstimates.getEstimateStatus("008585")).thenReturn(MarketEstimateApi.Status.TIMEOUT);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());
        PortfolioSummary summary = fundPnlService.computePortfolioSummary();

        assertThat(pnl.dailyChangePct()).isNull();
        assertThat(pnl.dailyPnl()).isNull();
        assertThat(pnl.holdingAmount()).isEqualByComparingTo("1853.4000");
        assertThat(pnl.totalPnl()).isEqualByComparingTo("53.4000");
        assertThat(pnl.estimateFetchFailed()).isTrue();
        assertThat(summary.dailyPnlTotal()).isNull();
        assertThat(summary.holdingAmountTotal()).isEqualByComparingTo("1853.4000");
        assertThat(summary.dailyCoveredFundCount()).isZero();
        assertThat(summary.estimateFetchFailedCount()).isEqualTo(1);
    }

    @Test
    @Transactional
    void 单基金_当日净值已入库_忽略历史估值失败状态() {
        FundEntity fund = persistHoldingFundWithCode("008585", "华夏人工智能ETF联接A");
        navHistory(fund, Instant.parse("2026-07-05T00:00:00Z"), "1.80");
        navHistory(fund, Instant.parse("2026-07-06T00:00:00Z"), "1.89");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1800", FundTransactionStatus.CONFIRMED);
        fund.setCostPerShare(new BigDecimal("1.80"));
        fundRepository.save(fund);
        when(marketEstimates.getEstimateStatus("008585")).thenReturn(MarketEstimateApi.Status.TIMEOUT);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        assertThat(pnl.dailyChangePct()).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.0001")));
        assertThat(pnl.dailyPnl()).isCloseTo(new BigDecimal("90"), within(new BigDecimal("0.01")));
        assertThat(pnl.estimateFetchFailed()).isFalse();
    }

    @Test
    @Transactional
    void 单基金_估值预热失败_按最近确认净值展示仓位() {
        FundEntity fund = persistHoldingFundWithCode("008585", "华夏人工智能ETF联接A");
        navHistory(fund, Instant.parse("2026-07-02T00:00:00Z"), "1.84");
        navHistory(fund, Instant.parse("2026-07-03T00:00:00Z"), "1.8534");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1800", FundTransactionStatus.CONFIRMED);
        fund.setCostPerShare(new BigDecimal("1.80"));
        fundRepository.save(fund);
        when(clock.instant()).thenReturn(Instant.parse("2026-07-06T00:00:00Z"));
        when(marketEstimates.getEstimateStatus("008585")).thenReturn(MarketEstimateApi.Status.TIMEOUT);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        assertThat(pnl.dailyChangePct()).isNull();
        assertThat(pnl.holdingAmount()).isEqualByComparingTo("1853.4000");
        assertThat(pnl.totalPnl()).isEqualByComparingTo("53.4000");
        assertThat(pnl.estimateFetchFailed()).isTrue();
    }

    private FundEntity persistHoldingFund() {
        return persistHoldingFundWithCode("510300", "沪深300ETF");
    }

    private FundEntity persistHoldingFundWithCode(String code, String name) {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(testActorId());
        fund.setFundCode(code);
        fund.setFundName(name);
        fund.setStatus(FundStatus.HOLDING);
        return fundRepository.save(fund);
    }

    private FundEntity persistPendingFund() {
        FundEntity fund = new FundEntity();
        fund.setOwnerId(testActorId());
        fund.setFundCode("159825");
        fund.setFundName("半导体ETF");
        fund.setStatus(FundStatus.PENDING_HOLDING);
        return fundRepository.save(fund);
    }

    private void navHistory(FundEntity fund, Instant navDate, String accumulatedNav) {
        navHistory(fund, navDate, accumulatedNav, accumulatedNav);
    }

    private void navHistory(FundEntity fund, Instant navDate, String unitNav, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(navDate);
        entity.setNav(new BigDecimal(unitNav));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        fundNavHistoryRepository.save(entity);
    }

    private FundTransactionEntity txWithAmount(FundEntity fund, FundTransactionSource source,
                                               String shares, String amount, FundTransactionStatus status) {
        FundTransactionEntity entity = new FundTransactionEntity();
        entity.setFundEntity(fund);
        entity.setSource(source);
        entity.setStatus(status);
        entity.setShares(new BigDecimal(shares));
        entity.setAmount(new BigDecimal(amount));
        entity.setNav(new BigDecimal("1.20"));
        return fundTransactionRepository.save(entity);
    }

    /** 当前 UTC 日期往前推 n 天的 Instant(00:00:00Z),用作净值日期,避免硬编码过期日期。 */
    private static Instant daysAgo(int n) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(n)
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }
}
