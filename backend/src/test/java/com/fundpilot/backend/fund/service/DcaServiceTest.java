package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 定投扣款服务集成测试(issue #61):验证每月扣款产生 INVEST、无金额跳过、行业止盈暂停。
 */
@Transactional
class DcaServiceTest extends AbstractIntegrationTest {

    @Autowired DcaService dcaService;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired EntityManager entityManager;

    @Test
    void 持仓基金有定投金额_产生INVEST交易() {
        FundEntity fund = persistHoldingFund(FundCategory.BROAD_BASE, new BigDecimal("1000"));
        entityManager.flush();
        entityManager.clear();

        int count = dcaService.investMonthly();

        assertThat(count).isEqualTo(1);
        FundTransactionEntity tx = fundTransactionRepository.findByFundEntity_Id(fund.getId()).get(0);
        assertThat(tx.getSource()).isEqualTo(FundTransactionSource.INVEST);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(tx.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        assertThat(tx.getShares()).isNull(); // 由 NavConfirmJob 回填
        assertThat(tx.getSignalLogEntity()).isNull(); // 定投不经信号
    }

    @Test
    void 无定投金额_跳过不产生交易() {
        FundEntity fund = persistHoldingFund(FundCategory.BROAD_BASE, null);
        entityManager.flush();
        entityManager.clear();

        int count = dcaService.investMonthly();

        assertThat(count).isZero();
        assertThat(fundTransactionRepository.findByFundEntity_Id(fund.getId())).isEmpty();
    }

    @Test
    void 行业最近SELL且收益率高于10百分比_暂停定投() {
        FundEntity fund = persistHoldingFund(FundCategory.SECTOR, new BigDecimal("1000"));
        // 持仓 1000 份,累计投入 1000,净值 1.5 → 市值 1500,yield 50% > 10%
        persistConfirmedBuy(fund, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("1.0"));
        persistNav(fund, new BigDecimal("1.5"));
        persistConfirmedDecrease(fund, new BigDecimal("250"), new BigDecimal("1.5"));
        entityManager.flush();
        entityManager.clear();

        int count = dcaService.investMonthly();

        // 行业止盈后 yield>10% → 暂停,不产生 INVEST
        assertThat(count).isZero();
    }

    @Test
    void 行业最近SELL但收益率低于10百分比_恢复定投() {
        FundEntity fund = persistHoldingFund(FundCategory.SECTOR, new BigDecimal("1000"));
        // 持仓 1000 份,投入 1000,净值 0.9 → 市值 900,yield -10% ≤ 10% → 恢复
        persistConfirmedBuy(fund, new BigDecimal("1000"), new BigDecimal("1000"), new BigDecimal("1.0"));
        persistNav(fund, new BigDecimal("0.9"));
        persistConfirmedDecrease(fund, new BigDecimal("250"), new BigDecimal("0.9"));
        entityManager.flush();
        entityManager.clear();

        int count = dcaService.investMonthly();

        assertThat(count).isEqualTo(1); // 恢复定投
    }

    private FundEntity persistHoldingFund(FundCategory category, BigDecimal dcaAmount) {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setFundCategory(category);
        fund.setStatus(FundStatus.HOLDING);
        fund.setDcaAmount(dcaAmount);
        fund.setOpenedAt(Instant.parse("2025-01-01T00:00:00Z"));
        entityManager.persist(fund);
        return fund;
    }

    private void persistConfirmedBuy(FundEntity fund, BigDecimal amount, BigDecimal shares, BigDecimal nav) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setAmount(amount);
        tx.setShares(shares);
        tx.setNav(nav);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        tx.setConfirmTime(Instant.parse("2025-01-01T00:00:00Z"));
        entityManager.persist(tx);
    }

    /** 止盈卖出交易(DECREASE,CONFIRMED):行业暂停判定的持久触发源。 */
    private void persistConfirmedDecrease(FundEntity fund, BigDecimal shares, BigDecimal nav) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.DECREASE);
        tx.setShares(shares);
        tx.setAmount(shares.multiply(nav));
        tx.setNav(nav);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        tx.setConfirmTime(Instant.parse("2025-05-15T00:00:00Z"));
        entityManager.persist(tx);
    }

    private void persistNav(FundEntity fund, BigDecimal accumulatedNav) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setNavDate(Instant.parse("2025-06-01T00:00:00Z"));
        nav.setNav(accumulatedNav);
        nav.setAccumulatedNav(accumulatedNav);
        entityManager.persist(nav);
    }
}