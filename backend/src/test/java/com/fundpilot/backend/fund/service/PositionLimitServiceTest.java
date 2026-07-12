package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.user.service.UserConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PositionLimitServiceTest {

    @Mock FundRepository fundRepository;
    @Mock FundTransactionRepository fundTransactionRepository;
    @Mock UserConfigService userConfigService;

    private PositionLimitService service;

    @BeforeEach
    void setUp() {
        service = new PositionLimitService(fundRepository, fundTransactionRepository, userConfigService);
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setMaxPositionRatio(new BigDecimal("0.30"));
        when(fundRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(fund));
        when(userConfigService.requireTotalCapital()).thenReturn(new BigDecimal("10000"));
    }

    @Test
    void validatePurchase_买入后恰好达到上限_允许() {
        when(fundTransactionRepository.aggregateConfirmedShares(List.of(1L)))
                .thenReturn(List.of(projection("2000")));

        assertThatCode(() -> service.validatePurchase(1L, new BigDecimal("1000"), BigDecimal.ONE))
                .doesNotThrowAnyException();
    }

    @Test
    void validatePurchase_买入后超过上限_拒绝() {
        when(fundTransactionRepository.aggregateConfirmedShares(List.of(1L)))
                .thenReturn(List.of(projection("2000")));

        assertThatThrownBy(() -> service.validatePurchase(1L, new BigDecimal("1000.01"), BigDecimal.ONE))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.POSITION_LIMIT_EXCEEDED.name());
    }

    private static FundTransactionRepository.HoldingSharesProjection projection(String shares) {
        return new FundTransactionRepository.HoldingSharesProjection() {
            @Override public Long getFundId() { return 1L; }
            @Override public BigDecimal getHoldingShares() { return new BigDecimal(shares); }
        };
    }
}
