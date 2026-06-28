package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
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
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * issue #9 循环 A:{@code FundPositionService} 持仓份额 + 在途份额聚合。
 * <p>direction:INCREASE/TRANSFER_IN/INVEST = +1,DECREASE/TRANSFER_OUT = -1。
 * 聚合只看 CONFIRMED 进持仓;PENDING 算在途;CANCELLED 不计。
 */
class FundPositionServiceTest extends AbstractIntegrationTest {

    @Autowired
    FundPositionService fundPositionService;

    @Autowired
    FundRepository fundRepository;

    @Autowired
    FundTransactionRepository fundTransactionRepository;

    @Autowired
    FundNavHistoryRepository fundNavHistoryRepository;

    @Test
    @Transactional
    void 多笔CONFIRMED买入卖出_持仓份额等于_Σ_direction() {
        FundEntity fund = persistFund();
        // 买入 100 + 买入 50 - 卖出 30 = 120
        tx(fund, FundTransactionSource.INCREASE, "100", FundTransactionStatus.CONFIRMED);
        tx(fund, FundTransactionSource.INCREASE, "50", FundTransactionStatus.CONFIRMED);
        tx(fund, FundTransactionSource.DECREASE, "30", FundTransactionStatus.CONFIRMED);

        BigDecimal shares = fundPositionService.getHoldingShares(fund.getId());

        assertThat(shares).isCloseTo(new BigDecimal("120"), within(new BigDecimal("0.0001")));
    }

    @Test
    @Transactional
    void PENDING_行算在途_不进持仓份额() {
        FundEntity fund = persistFund();
        tx(fund, FundTransactionSource.INCREASE, "100", FundTransactionStatus.CONFIRMED);
        tx(fund, FundTransactionSource.INCREASE, "50", FundTransactionStatus.PENDING);

        BigDecimal holding = fundPositionService.getHoldingShares(fund.getId());
        BigDecimal pending = fundPositionService.getPendingShares(fund.getId());

        assertThat(holding).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.0001")));
        assertThat(pending).isCloseTo(new BigDecimal("50"), within(new BigDecimal("0.0001")));
    }

    @Test
    @Transactional
    void CANCELLED_行不计入持仓也不计入在途() {
        FundEntity fund = persistFund();
        tx(fund, FundTransactionSource.INCREASE, "100", FundTransactionStatus.CONFIRMED);
        tx(fund, FundTransactionSource.DECREASE, "30", FundTransactionStatus.CANCELLED);

        BigDecimal holding = fundPositionService.getHoldingShares(fund.getId());
        BigDecimal pending = fundPositionService.getPendingShares(fund.getId());

        assertThat(holding).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.0001")));
        assertThat(pending).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @Transactional
    void 转账两腿互指_份额正确加减() {
        FundEntity fund = persistFund();
        FundTransactionEntity transferIn = tx(fund, FundTransactionSource.TRANSFER_IN, "200", FundTransactionStatus.CONFIRMED);
        FundTransactionEntity transferOut = tx(fund, FundTransactionSource.TRANSFER_OUT, "200", FundTransactionStatus.CONFIRMED);
        transferIn.setRelatedFundTransactionEntity(transferOut);
        transferOut.setRelatedFundTransactionEntity(transferIn);
        fundTransactionRepository.save(transferIn);
        fundTransactionRepository.save(transferOut);

        BigDecimal shares = fundPositionService.getHoldingShares(fund.getId());

        // 转入 +200,转出 -200,净 0
        assertThat(shares).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @Transactional
    void INVEST_定投_按加仓_正方向_计入持仓() {
        FundEntity fund = persistFund();
        tx(fund, FundTransactionSource.INVEST, "80", FundTransactionStatus.CONFIRMED);

        BigDecimal shares = fundPositionService.getHoldingShares(fund.getId());

        assertThat(shares).isCloseTo(new BigDecimal("80"), within(new BigDecimal("0.0001")));
    }

    // ===== 循环 B:持仓金额 / 占比 / 成本 =====

    @Test
    @Transactional
    void 持仓金额_等于持仓份额乘以传入净值() {
        FundEntity fund = persistFund();
        tx(fund, FundTransactionSource.INCREASE, "100", FundTransactionStatus.CONFIRMED);

        BigDecimal amount = fundPositionService.getHoldingAmount(fund.getId(), new BigDecimal("1.50"));

        // 100 份 × 1.50 = 150
        assertThat(amount).isCloseTo(new BigDecimal("150"), within(new BigDecimal("0.01")));
    }

    @Test
    @Transactional
    void 持仓金额_随传入净值变化() {
        FundEntity fund = persistFund();
        tx(fund, FundTransactionSource.INCREASE, "100", FundTransactionStatus.CONFIRMED);

        BigDecimal atLowNav = fundPositionService.getHoldingAmount(fund.getId(), new BigDecimal("1.00"));
        BigDecimal atHighNav = fundPositionService.getHoldingAmount(fund.getId(), new BigDecimal("2.00"));

        assertThat(atLowNav).isCloseTo(new BigDecimal("100"), within(new BigDecimal("0.01")));
        assertThat(atHighNav).isCloseTo(new BigDecimal("200"), within(new BigDecimal("0.01")));
    }

    @Test
    @Transactional
    void 持仓占比_等于持仓金额除以总权益() {
        FundEntity fund = persistFund();
        tx(fund, FundTransactionSource.INCREASE, "100", FundTransactionStatus.CONFIRMED);

        // 持仓金额 = 100 × 1.50 = 150;总权益 1000 → 占比 0.15
        BigDecimal ratio = fundPositionService.getPositionRatio(fund.getId(), new BigDecimal("1.50"), new BigDecimal("1000"));

        assertThat(ratio).isCloseTo(new BigDecimal("0.15"), within(new BigDecimal("0.0001")));
    }

    @Test
    @Transactional
    void 多笔CONFIRMED交易_金额带方向聚合_验证方向逻辑() {
        FundEntity fund = persistFund();
        // 买入金额 100 + 买入金额 60 - 卖出金额 30 = 130(名义投入汇总)
        txWithAmount(fund, FundTransactionSource.INCREASE, "100", "100", FundTransactionStatus.CONFIRMED);
        txWithAmount(fund, FundTransactionSource.INCREASE, "60", "60", FundTransactionStatus.CONFIRMED);
        txWithAmount(fund, FundTransactionSource.DECREASE, "30", "30", FundTransactionStatus.CONFIRMED);
        // PENDING 不计入
        txWithAmount(fund, FundTransactionSource.INCREASE, "50", "50", FundTransactionStatus.PENDING);

        // 份额 = 100 + 60 - 30 = 130 份(验证方向逻辑仍正确)
        BigDecimal shares = fundPositionService.getHoldingShares(fund.getId());

        assertThat(shares).isCloseTo(new BigDecimal("130"), within(new BigDecimal("0.01")));
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("161725");
        fund.setFundName("测试基金");
        return fundRepository.save(fund);
    }

    private FundTransactionEntity tx(FundEntity fund, FundTransactionSource source, String shares, FundTransactionStatus status) {
        return txWithAmount(fund, source, shares, shares, status);
    }

    private FundTransactionEntity txWithAmount(FundEntity fund, FundTransactionSource source, String shares, String amount, FundTransactionStatus status) {
        FundTransactionEntity entity = new FundTransactionEntity();
        entity.setFundEntity(fund);
        entity.setSource(source);
        entity.setStatus(status);
        entity.setShares(new BigDecimal(shares));
        entity.setAmount(new BigDecimal(amount));
        entity.setNav(new BigDecimal("1.00"));
        return fundTransactionRepository.save(entity);
    }

    // ===== 循环 C:峰值派生 =====

    @Test
    @Transactional
    void peakNav_取_fund_nav_history_accumulated_nav_最大值() {
        FundEntity fund = persistFund();
        navHistory(fund, Instant.parse("2025-01-01T00:00:00Z"), "1.00", "1.00");
        navHistory(fund, Instant.parse("2025-02-01T00:00:00Z"), "1.50", "1.50");
        navHistory(fund, Instant.parse("2025-03-01T00:00:00Z"), "1.20", "1.20");

        var peakNav = fundPositionService.getPeakNav(fund.getId());

        assertThat(peakNav).hasValueSatisfying(v ->
                assertThat(v).isEqualByComparingTo(new BigDecimal("1.50")));
    }

    @Test
    @Transactional
    void holdingPeriodPeakNav_加_openedAt_过滤_只取持仓期内峰值() {
        FundEntity fund = persistFund();
        // openedAt = 2025-02-01,之前的净值 1.80 高于持仓期内任何值,但不应计入
        fund.setOpenedAt(Instant.parse("2025-02-01T00:00:00Z"));
        fundRepository.save(fund);
        navHistory(fund, Instant.parse("2025-01-01T00:00:00Z"), "1.80", "1.80");
        navHistory(fund, Instant.parse("2025-02-15T00:00:00Z"), "1.20", "1.20");
        navHistory(fund, Instant.parse("2025-03-01T00:00:00Z"), "1.40", "1.40");

        var peakNav = fundPositionService.getHoldingPeriodPeakNav(fund.getId());

        // 持仓期内峰值 = 1.40(1.80 在 openedAt 之前,排除)
        assertThat(peakNav).hasValueSatisfying(v ->
                assertThat(v).isEqualByComparingTo(new BigDecimal("1.40")));
    }

    @Test
    @Transactional
    void peakNav_无净值历史_返回_empty() {
        FundEntity fund = persistFund();

        var peakNav = fundPositionService.getPeakNav(fund.getId());

        assertThat(peakNav).isEmpty();
    }

    private void navHistory(FundEntity fund, Instant navDate, String nav, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(navDate);
        entity.setNav(new BigDecimal(nav));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        fundNavHistoryRepository.save(entity);
    }
}
