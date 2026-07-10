package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.signal.controller.ConfirmOperationRequest;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalOperationServiceUnitTest {

    @Mock SignalLogRepository signalLogRepository;
    @Mock FundTransactionRepository fundTransactionRepository;

    private SignalOperationService service;

    @BeforeEach
    void setUp() {
        service = new SignalOperationService(signalLogRepository, fundTransactionRepository);
    }

    @Test
    void confirmOperation_路径基金与信号基金不一致时拒绝() {
        SignalLogEntity signal = signal(11L, 2L, SignalType.SELL, SignalReason.LOGIC_BROKEN);
        when(signalLogRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(signal));

        assertThatThrownBy(() -> service.confirmOperation(1L, 11L,
                new ConfirmOperationRequest(11L, null, new BigDecimal("100"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SIGNAL_FUND_MISMATCH.name()));
    }

    @Test
    void confirmOperation_同一信号已有关联交易时拒绝() {
        SignalLogEntity signal = signal(11L, 1L, SignalType.SELL, SignalReason.LOGIC_BROKEN);
        when(signalLogRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(signal));
        when(fundTransactionRepository.existsBySignalLogEntity_Id(11L)).thenReturn(true);

        assertThatThrownBy(() -> service.confirmOperation(1L, 11L,
                new ConfirmOperationRequest(11L, null, new BigDecimal("100"))))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.SIGNAL_ALREADY_RESPONDED.name()));
    }

    @Test
    void confirmOperation_SELL交易保留SignalLog关联() {
        SignalLogEntity signal = signal(11L, 1L, SignalType.SELL, SignalReason.TRAILING_STOP);
        when(signalLogRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(signal));
        when(fundTransactionRepository.save(any(FundTransactionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FundTransactionEntity transaction = service.confirmOperation(1L, 11L,
                new ConfirmOperationRequest(11L, null, new BigDecimal("100")));

        assertThat(transaction.getSource()).isEqualTo(FundTransactionSource.DECREASE);
        assertThat(transaction.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        assertThat(transaction.getShares()).isEqualByComparingTo("100");
        assertThat(transaction.getSignalLogEntity()).isSameAs(signal);
        verify(fundTransactionRepository).existsBySignalLogEntity_Id(11L);
    }

    private static SignalLogEntity signal(Long signalId, Long fundId, SignalType type, SignalReason reason) {
        FundEntity fund = new FundEntity();
        fund.setId(fundId);
        SignalLogEntity signal = new SignalLogEntity();
        signal.setId(signalId);
        signal.setFundEntity(fund);
        signal.setSignalType(type);
        signal.setReason(reason);
        return signal;
    }
}
