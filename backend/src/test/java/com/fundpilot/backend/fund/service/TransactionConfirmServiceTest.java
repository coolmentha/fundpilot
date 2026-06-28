package com.fundpilot.backend.fund.service;

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
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * 手动确认交易集成测试:PENDING → CONFIRMED,取最新净值回填另一侧,转换交易两条腿联动。
 */
class TransactionConfirmServiceTest extends AbstractIntegrationTest {

    @Autowired TransactionConfirmService transactionConfirmService;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;
    @Autowired FundNavHistoryRepository fundNavHistoryRepository;

    @Test
    @Transactional
    void confirm_买入交易_用最新净值回填shares() {
        FundEntity fund = persistFund();
        navHistory(fund, "1.26");
        // 买入 1000 元,净值 1.26 → shares = 1000/1.26 ≈ 793.65
        FundTransactionEntity tx = persistPendingTx(fund, FundTransactionSource.INCREASE, new BigDecimal("1000"), null);

        List<FundTransactionEntity> confirmed = transactionConfirmService.confirm(tx.getId());

        assertThat(confirmed).hasSize(1);
        FundTransactionEntity reloaded = fundTransactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(reloaded.getNav()).isEqualByComparingTo("1.26");
        assertThat(reloaded.getShares()).isCloseTo(new BigDecimal("793.65"), within(new BigDecimal("0.01")));
        assertThat(reloaded.getConfirmTime()).isNotNull();
    }

    @Test
    @Transactional
    void confirm_卖出交易_用最新净值回填amount() {
        FundEntity fund = persistFund();
        navHistory(fund, "1.26");
        // 卖出 500 份,净值 1.26 → amount = 500 × 1.26 = 630
        FundTransactionEntity tx = persistPendingTx(fund, FundTransactionSource.DECREASE, null, new BigDecimal("500"));

        transactionConfirmService.confirm(tx.getId());

        FundTransactionEntity reloaded = fundTransactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(reloaded.getNav()).isEqualByComparingTo("1.26");
        assertThat(reloaded.getAmount()).isEqualByComparingTo("630");
    }

    @Test
    @Transactional
    void confirm_无净值历史_抛异常() {
        FundEntity fund = persistFund();
        FundTransactionEntity tx = persistPendingTx(fund, FundTransactionSource.INCREASE, new BigDecimal("1000"), null);

        assertThatThrownBy(() -> transactionConfirmService.confirm(tx.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.NAV_HISTORY_EMPTY.name());
    }

    @Test
    @Transactional
    void confirm_已确认交易_抛异常() {
        FundEntity fund = persistFund();
        navHistory(fund, "1.26");
        FundTransactionEntity tx = persistPendingTx(fund, FundTransactionSource.INCREASE, new BigDecimal("1000"), null);
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        fundTransactionRepository.save(tx);

        assertThatThrownBy(() -> transactionConfirmService.confirm(tx.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.TRANSACTION_ALREADY_CONFIRMED.name());
    }

    @Test
    @Transactional
    void confirm_转换交易_两条腿联动确认() {
        FundEntity fund = persistFund();
        navHistory(fund, "1.26");
        // 转入 1000 元 + 转出 500 份,互指 relatedTransaction
        FundTransactionEntity tin = persistPendingTx(fund, FundTransactionSource.TRANSFER_IN, new BigDecimal("1000"), null);
        FundTransactionEntity tout = persistPendingTx(fund, FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("500"));
        tin.setRelatedFundTransactionEntity(tout);
        tout.setRelatedFundTransactionEntity(tin);
        fundTransactionRepository.save(tin);
        fundTransactionRepository.save(tout);

        List<FundTransactionEntity> confirmed = transactionConfirmService.confirm(tin.getId());

        // 两条腿都确认
        assertThat(confirmed).hasSize(2);
        assertThat(confirmed).extracting(FundTransactionEntity::getStatus)
                .containsOnly(FundTransactionStatus.CONFIRMED);
        FundTransactionEntity reloadedTout = fundTransactionRepository.findById(tout.getId()).orElseThrow();
        assertThat(reloadedTout.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(reloadedTout.getAmount()).isEqualByComparingTo("630"); // 500 × 1.26
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setStatus(FundStatus.HOLDING);
        return fundRepository.save(fund);
    }

    private void navHistory(FundEntity fund, String accumulatedNav) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setFundEntity(fund);
        entity.setNavDate(Instant.parse("2025-06-27T00:00:00Z"));
        entity.setNav(new BigDecimal(accumulatedNav));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        fundNavHistoryRepository.save(entity);
    }

    private FundTransactionEntity persistPendingTx(FundEntity fund, FundTransactionSource source,
                                                    BigDecimal amount, BigDecimal shares) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setAmount(amount);
        tx.setShares(shares);
        return fundTransactionRepository.save(tx);
    }
}
