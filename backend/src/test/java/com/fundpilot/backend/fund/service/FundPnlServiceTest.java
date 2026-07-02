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
import com.fundpilot.backend.market.service.FundEstimateService;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * issue #18 盈亏/涨跌多表聚合集成测试(CONTEXT.md「今日涨跌/今日盈亏/总盈亏」)。
 * <p>落 fund_nav_history 最近两期累计净值 + CONFIRMED 交易,验证 FundPnlService 聚合。
 * 算术委托 {@link com.fundpilot.backend.fund.service.support.FundPnlCalculator},本类只验多表拼装。
 */
class FundPnlServiceTest extends AbstractIntegrationTest {

    /** issue #38:mock FundEstimateService 返 empty,让三态降级到落库净值算(隔离网络,恢复原数值断言)。 */
    @MockitoBean
    FundEstimateService fundEstimateService;

    @Autowired FundPnlService fundPnlService;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired FundNavHistoryRepository fundNavHistoryRepository;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(fundEstimateService.fetchEstimate(anyString())).thenReturn(Optional.empty());
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
        navHistory(fund, yesterdayUtc(), "1.20");
        navHistory(fund, todayUtc(), "1.26");
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
    void 无净值历史_涨跌与盈亏字段为null() {
        FundEntity fund = persistHoldingFund();
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1200", FundTransactionStatus.CONFIRMED);

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        assertThat(pnl.dailyChangePct()).isNull();
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
        navHistory(fund, yesterdayUtc(), "1.20");
        navHistory(fund, todayUtc(), "1.26");

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
        FundEntity fundA = persistHoldingFundWithCode("510300", "沪深300ETF");
        navHistory(fundA, yesterdayUtc(), "1.20");
        navHistory(fundA, todayUtc(), "1.26");
        txWithAmount(fundA, FundTransactionSource.INCREASE, "1000", "1200", FundTransactionStatus.CONFIRMED);
        fundA.setCostPerShare(new BigDecimal("1.20"));
        fundRepository.save(fundA);

        // 基金B:今日下跌 -2%(1.00→0.98),持仓1000份 成本单价1.00 → 今日盈亏-20 总盈亏-20(亏)
        FundEntity fundB = persistHoldingFundWithCode("159825", "半导体ETF");
        navHistory(fundB, yesterdayUtc(), "1.00");
        navHistory(fundB, todayUtc(), "0.98");
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

    /** 当天 UTC 0 点,与 FundPnlService#isTodayNavConfirmed 的 today 同口径(避免跑测时刻漂移)。 */
    private static Instant todayUtc() {
        return ZonedDateTime.now(ZoneOffset.UTC).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    /** 昨天 UTC 0 点,作 previousNav。 */
    private static Instant yesterdayUtc() {
        return todayUtc().minus(Duration.ofDays(1));
    }

    private FundEntity persistHoldingFund() {
        return persistHoldingFundWithCode("510300", "沪深300ETF");
    }

    private FundEntity persistHoldingFundWithCode(String code, String name) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName(name);
        fund.setStatus(FundStatus.HOLDING);
        return fundRepository.save(fund);
    }

    private FundEntity persistPendingFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("159825");
        fund.setFundName("半导体ETF");
        fund.setStatus(FundStatus.PENDING_HOLDING);
        return fundRepository.save(fund);
    }

    private void navHistory(FundEntity fund, Instant navDate, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(navDate);
        entity.setNav(new BigDecimal(accumulatedNav));
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
}
