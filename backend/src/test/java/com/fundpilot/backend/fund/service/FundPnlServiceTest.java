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
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #18 盈亏/涨跌多表聚合集成测试(CONTEXT.md「今日涨跌/今日盈亏/总盈亏」)。
 * <p>落 fund_nav_history 最近两期累计净值 + CONFIRMED 交易,验证 FundPnlService 聚合。
 * 算术委托 {@link com.fundpilot.backend.fund.service.support.FundPnlCalculator},本类只验多表拼装。
 */
class FundPnlServiceTest extends AbstractIntegrationTest {

    @Autowired FundPnlService fundPnlService;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired FundNavHistoryRepository fundNavHistoryRepository;

    @Test
    @Transactional
    void 持仓基金_聚合今日涨跌今日盈亏总盈亏() {
        FundEntity fund = persistHoldingFund();
        // 累计净值 1.20 → 1.26(涨 5%);持仓 1000 份;成本 1200;市值 1260;总盈亏 +60
        navHistory(fund, Instant.parse("2025-06-01T00:00:00Z"), "1.20");
        navHistory(fund, Instant.parse("2025-06-02T00:00:00Z"), "1.26");
        txWithAmount(fund, FundTransactionSource.INCREASE, "1000", "1200", FundTransactionStatus.CONFIRMED);

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
        navHistory(fund, Instant.parse("2025-06-01T00:00:00Z"), "1.20");
        navHistory(fund, Instant.parse("2025-06-02T00:00:00Z"), "1.26");

        FundPnlService.Pnl pnl = fundPnlService.computeForFund(fund.getId());

        // story 21:未建仓基金也能看今日涨跌
        assertThat(pnl.dailyChangePct()).isCloseTo(new BigDecimal("0.05"), within(new BigDecimal("0.0001")));
        // 无持仓:份额/市值/盈亏为 null
        assertThat(pnl.holdingShares()).isNull();
        assertThat(pnl.holdingAmount()).isNull();
        assertThat(pnl.dailyPnl()).isNull();
        assertThat(pnl.totalPnl()).isNull();
    }

    private FundEntity persistHoldingFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
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
