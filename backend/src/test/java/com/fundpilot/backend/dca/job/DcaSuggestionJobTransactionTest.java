package com.fundpilot.backend.dca.job;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.dca.service.DcaSuggestionService;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class DcaSuggestionJobTransactionTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T06:55:00Z");

    @Autowired DcaSuggestionJob job;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;

    @MockitoBean FundDcaPlanRepository fundDcaPlanRepository;
    @MockitoBean TradingCalendarService tradingCalendarService;
    @MockitoBean Clock clock;
    @MockitoSpyBean DcaSuggestionService dcaSuggestionService;

    private final List<Long> createdFundIds = new ArrayList<>();

    @AfterEach
    void cleanUpCommittedRows() {
        for (Long fundId : createdFundIds) {
            fundTransactionRepository.deleteAll(fundTransactionRepository.findByFundEntity_Id(fundId));
        }
        createdFundIds.forEach(fundRepository::deleteById);
    }

    @Test
    void run_每只基金经过独立事务代理_单只失败后继续生成() {
        FundEntity failedFund = persistFund("161725", "失败基金");
        FundEntity successfulFund = persistFund("161726", "成功基金");
        FundDcaPlanEntity plan = dailyPlan(successfulFund, 22L);
        when(clock.instant()).thenReturn(NOW);
        when(tradingCalendarService.isTradingDay(any())).thenReturn(true);
        when(fundDcaPlanRepository.findEffectiveFundIds())
                .thenReturn(List.of(failedFund.getId(), successfulFund.getId()));
        when(fundDcaPlanRepository.findByFundEntity_IdAndStatus(
                successfulFund.getId(), DcaPlanStatus.EFFECTIVE)).thenReturn(Optional.of(plan));

        AtomicBoolean transactionActive = new AtomicBoolean();
        doAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            return invocation.callRealMethod();
        }).when(dcaSuggestionService).generateForFund(successfulFund.getId(), NOW);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("expected failure");
        }).when(dcaSuggestionService).generateForFund(failedFund.getId(), NOW);

        job.run();

        assertThat(transactionActive).isTrue();
        assertThat(fundTransactionRepository.findByFundEntity_IdAndStatus(
                successfulFund.getId(), FundTransactionStatus.PENDING)).hasSize(1);
        assertThat(fundTransactionRepository.findByFundEntity_Id(failedFund.getId())).isEmpty();
    }

    private FundEntity persistFund(String code, String name) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(code);
        fund.setFundName(name);
        FundEntity saved = fundRepository.saveAndFlush(fund);
        createdFundIds.add(saved.getId());
        return saved;
    }

    private static FundDcaPlanEntity dailyPlan(FundEntity fund, Long id) {
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setId(id);
        plan.setFundEntity(fund);
        plan.setStatus(DcaPlanStatus.EFFECTIVE);
        plan.setEnabled(true);
        plan.setFrequency(DcaFrequency.DAILY);
        plan.setAmount(new BigDecimal("1000"));
        return plan;
    }
}
