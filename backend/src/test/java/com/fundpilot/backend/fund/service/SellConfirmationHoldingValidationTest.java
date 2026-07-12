package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellConfirmationHoldingValidationTest extends AbstractIntegrationTest {

    @Autowired TransactionConfirmService transactionConfirmService;
    @Autowired NavConfirmService navConfirmService;
    @Autowired FundPositionService fundPositionService;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired FundNavHistoryRepository fundNavHistoryRepository;
    @Autowired SignalLogRepository signalLogRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE fund CASCADE");
    }

    @Test
    void confirm_无Lot且无事实持仓_拒绝卖出() {
        FundEntity fund = persistFund();
        persistNav(fund);
        FundTransactionEntity sell = persistPendingSell(fund, FundTransactionSource.DECREASE, "1");

        assertInsufficientHolding(sell);
    }

    @Test
    void confirm_仅有AdjustIn事实持仓_允许无Lot合法卖出() {
        FundEntity fund = persistFund();
        persistNav(fund);
        persistConfirmedShares(fund, FundTransactionSource.ADJUST_IN, "100");
        FundTransactionEntity sell = persistPendingSell(fund, FundTransactionSource.DECREASE, "40");

        transactionConfirmService.confirm(sell.getId());

        FundTransactionEntity confirmed = fundTransactionRepository.findById(sell.getId()).orElseThrow();
        assertThat(confirmed.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(confirmed.getAmount()).isEqualByComparingTo("40");
        assertThat(confirmed.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("60");
    }

    @Test
    void confirm_卖出超过Confirmed事实持仓_拒绝且不产生负持仓() {
        FundEntity fund = persistFund();
        persistNav(fund);
        persistConfirmedShares(fund, FundTransactionSource.ADJUST_IN, "100");
        FundTransactionEntity sell = persistPendingSell(fund, FundTransactionSource.TRANSFER_OUT, "100.01");

        assertInsufficientHolding(sell);
        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("100");
    }

    @Test
    void confirm_有收费Lot且卖出不超过事实持仓_正常确认() {
        FundEntity fund = persistFund();
        persistNav(fund);
        FundTransactionEntity buy = new FundTransactionEntity();
        buy.setFundEntity(fund);
        buy.setSource(FundTransactionSource.INCREASE);
        buy.setStatus(FundTransactionStatus.PENDING);
        buy.setAmount(new BigDecimal("100"));
        buy.setTradeDate(Instant.now());
        buy = fundTransactionRepository.save(buy);
        transactionConfirmService.confirm(buy.getId());
        FundTransactionEntity sell = persistPendingSell(fund, FundTransactionSource.DECREASE, "40");

        transactionConfirmService.confirm(sell.getId());

        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("60");
    }

    @Test
    void confirm_关联Sell信号的Decrease超过事实持仓_统一拒绝() {
        FundEntity fund = persistFund();
        persistNav(fund);
        persistConfirmedShares(fund, FundTransactionSource.ADJUST_IN, "10");
        SignalLogEntity signal = new SignalLogEntity();
        signal.setFundEntity(fund);
        signal.setSignalDate(ChinaTradingDate.toUtcDate(Instant.now()));
        signal.setSignalType(SignalType.SELL);
        signal.setReason(SignalReason.LOGIC_BROKEN);
        signal = signalLogRepository.save(signal);
        FundTransactionEntity sell = persistPendingSell(fund, FundTransactionSource.DECREASE, "11");
        sell.setSignalLogEntity(signal);
        sell = fundTransactionRepository.save(sell);

        assertInsufficientHolding(sell);
    }

    @Test
    void navConfirm_一只基金超卖失败_不阻断其他基金确认() {
        FundEntity invalidFund = persistFund();
        persistNav(invalidFund);
        persistConfirmedShares(invalidFund, FundTransactionSource.ADJUST_IN, "10");
        FundTransactionEntity invalidSell = persistPendingSell(
                invalidFund, FundTransactionSource.DECREASE, "10.01");

        FundEntity validFund = persistFund();
        persistNav(validFund);
        persistConfirmedShares(validFund, FundTransactionSource.ADJUST_IN, "10");
        FundTransactionEntity validSell = persistPendingSell(
                validFund, FundTransactionSource.DECREASE, "2");

        int confirmed = navConfirmService.confirmPendingTransactionsIsolated(
                ChinaTradingDate.toUtcDate(Instant.now()));

        assertThat(confirmed).isEqualTo(1);
        assertThat(fundTransactionRepository.findById(invalidSell.getId()).orElseThrow().getStatus())
                .isEqualTo(FundTransactionStatus.PENDING);
        assertThat(fundTransactionRepository.findById(validSell.getId()).orElseThrow().getStatus())
                .isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(fundPositionService.getHoldingShares(invalidFund.getId())).isEqualByComparingTo("10");
        assertThat(fundPositionService.getHoldingShares(validFund.getId())).isEqualByComparingTo("8");
    }

    private void assertInsufficientHolding(FundTransactionEntity sell) {
        assertThatThrownBy(() -> transactionConfirmService.confirm(sell.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INSUFFICIENT_HOLDING_SHARES.name());
        assertThat(sell.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setStatus(FundStatus.HOLDING);
        return fundRepository.save(fund);
    }

    private void persistNav(FundEntity fund) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setNavDate(ChinaTradingDate.toUtcDate(Instant.now()));
        nav.setNav(BigDecimal.ONE);
        nav.setAccumulatedNav(BigDecimal.ONE);
        fundNavHistoryRepository.save(nav);
    }

    private void persistConfirmedShares(FundEntity fund, FundTransactionSource source, String shares) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        tx.setShares(new BigDecimal(shares));
        tx.setTradeDate(Instant.now());
        tx.setConfirmTime(Instant.now());
        fundTransactionRepository.save(tx);
    }

    private FundTransactionEntity persistPendingSell(FundEntity fund, FundTransactionSource source, String shares) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setShares(new BigDecimal(shares));
        tx.setTradeDate(Instant.now());
        return fundTransactionRepository.save(tx);
    }
}
