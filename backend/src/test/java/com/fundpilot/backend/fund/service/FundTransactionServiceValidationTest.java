package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.fund.controller.ManualTransactionRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundTransactionServiceValidationTest {

    @Mock private FundTransactionRepository fundTransactionRepository;
    @Mock private FundRepository fundRepository;
    @Mock private TransactionConfirmSupport transactionConfirmSupport;
    @Mock private FundAccessService fundAccessService;
    @InjectMocks private FundTransactionService service;

    @BeforeEach
    void setUp() {
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
    }

    @Test
    void createManual_买入金额必须为正数() {
        assertInvalid(new ManualTransactionRequest(
                FundTransactionSource.INCREASE, BigDecimal.ZERO, null, null));
        assertInvalid(new ManualTransactionRequest(
                FundTransactionSource.INCREASE, new BigDecimal("-1"), null, null));
    }

    @Test
    void createManual_卖出份额必须为正数() {
        assertInvalid(new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, BigDecimal.ZERO, null));
        assertInvalid(new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("-1"), null));
    }

    private void assertInvalid(ManualTransactionRequest request) {
        assertThatThrownBy(() -> service.createManual(1L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("正数");
    }
}
