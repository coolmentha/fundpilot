package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundPositionServiceUnitTest {

    @Mock FundTransactionRepository fundTransactionRepository;
    @Mock FundNavHistoryRepository fundNavHistoryRepository;
    @Mock FundRepository fundRepository;

    private FundPositionService service;

    @BeforeEach
    void setUp() {
        service = new FundPositionService(
                fundTransactionRepository, fundNavHistoryRepository, fundRepository);
    }

    @Test
    void pending买入份额为空_按零聚合() {
        FundTransactionEntity transaction = transaction(1L, FundTransactionSource.INCREASE, null);
        when(fundTransactionRepository.findByFundEntity_IdAndStatus(1L, FundTransactionStatus.PENDING))
                .thenReturn(List.of(transaction));

        assertThat(service.getPendingShares(1L)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void 未跟踪份额_按确认账本FIFO重放() {
        when(fundTransactionRepository.findByFundEntity_IdAndStatus(1L, FundTransactionStatus.CONFIRMED))
                .thenReturn(List.of(
                        transaction(1L, FundTransactionSource.INCREASE, "100"),
                        transaction(2L, FundTransactionSource.ADJUST_IN, "50"),
                        transaction(3L, FundTransactionSource.DECREASE, "120")));

        assertThat(service.getUntrackedHoldingShares(1L)).isEqualByComparingTo("30");
    }

    @Test
    void 持仓期峰值_使用建仓所在北京时间日期标签() {
        FundEntity fund = new FundEntity();
        fund.setOpenedAt(Instant.parse("2025-02-01T06:30:00Z"));
        when(fundRepository.findById(1L)).thenReturn(Optional.of(fund));
        when(fundNavHistoryRepository.findPeakAccumulatedNavSince(
                1L, Instant.parse("2025-02-01T00:00:00Z")))
                .thenReturn(Optional.of(new BigDecimal("1.50")));

        assertThat(service.getHoldingPeriodPeakNav(1L))
                .contains(new BigDecimal("1.50"));
        verify(fundNavHistoryRepository).findPeakAccumulatedNavSince(
                1L, Instant.parse("2025-02-01T00:00:00Z"));
    }

    private static FundTransactionEntity transaction(
            Long id, FundTransactionSource source, String shares) {
        FundTransactionEntity transaction = new FundTransactionEntity();
        transaction.setId(id);
        transaction.setSource(source);
        transaction.setShares(shares == null ? null : new BigDecimal(shares));
        transaction.setTradeDate(Instant.parse("2026-07-01T00:00:00Z").plusSeconds(id));
        return transaction;
    }
}
