package com.fundpilot.backend.fund.repository;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FundTransactionRepositoryTest extends AbstractIntegrationTest {

    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;

    @Test
    @Transactional
    void findLatestConfirmedBuy_忽略更晚的卖出和待确认买入() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setStatus(FundStatus.HOLDING);
        fund = fundRepository.save(fund);

        FundTransactionEntity confirmedBuy = transaction(
                fund, FundTransactionSource.INCREASE, FundTransactionStatus.CONFIRMED,
                Instant.parse("2026-07-01T00:00:00Z"));
        transaction(fund, FundTransactionSource.DECREASE, FundTransactionStatus.CONFIRMED,
                Instant.parse("2026-07-05T00:00:00Z"));
        transaction(fund, FundTransactionSource.INVEST, FundTransactionStatus.PENDING, null);

        assertThat(fundTransactionRepository
                .findFirstByFundEntity_IdAndStatusAndSourceInAndConfirmTimeIsNotNullOrderByConfirmTimeDesc(
                        fund.getId(), FundTransactionStatus.CONFIRMED,
                        List.of(FundTransactionSource.INCREASE, FundTransactionSource.TRANSFER_IN,
                                FundTransactionSource.INVEST)))
                .contains(confirmedBuy);
    }

    private FundTransactionEntity transaction(FundEntity fund, FundTransactionSource source,
                                               FundTransactionStatus status, Instant confirmTime) {
        FundTransactionEntity transaction = new FundTransactionEntity();
        transaction.setFundEntity(fund);
        transaction.setSource(source);
        transaction.setStatus(status);
        transaction.setConfirmTime(confirmTime);
        return fundTransactionRepository.save(transaction);
    }
}
