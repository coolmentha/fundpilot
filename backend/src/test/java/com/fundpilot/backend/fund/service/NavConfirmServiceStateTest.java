package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NavConfirmServiceStateTest {

    @Mock private FundTransactionRepository fundTransactionRepository;
    @Mock private FundNavHistoryRepository fundNavHistoryRepository;
    @Mock private TransactionConfirmSupport transactionConfirmSupport;
    @Mock private FundPositionService fundPositionService;
    @InjectMocks private NavConfirmService service;

    @Test
    void confirmPendingTransactions_转换任一腿缺净值时保持两腿Pending() {
        Instant day = Instant.parse("2026-07-09T00:00:00Z");
        Instant end = day.plus(1, ChronoUnit.DAYS);
        FundEntity fundA = fund(1L);
        FundEntity fundB = fund(2L);
        FundTransactionEntity out = tx(10L, fundA, FundTransactionSource.TRANSFER_OUT);
        out.setShares(new BigDecimal("500"));
        FundTransactionEntity in = tx(11L, fundB, FundTransactionSource.TRANSFER_IN);
        out.setRelatedFundTransactionEntity(in);
        in.setRelatedFundTransactionEntity(out);

        when(fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING))
                .thenReturn(List.of(out, in));
        when(fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetween(1L, day, end))
                .thenReturn(List.of(nav(fundA, "1.25")));
        when(fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetween(2L, day, end))
                .thenReturn(List.of());

        int confirmed = service.confirmPendingTransactions(day);

        assertThat(confirmed).isZero();
        assertThat(out.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        assertThat(in.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        verify(transactionConfirmSupport, never()).onSellConfirmed(eq(out), any());
    }

    @Test
    void confirmPendingTransactions_旧交易使用自身创建日净值而非任务Fallback日期() {
        Instant friday = Instant.parse("2026-07-10T00:00:00Z");
        Instant monday = Instant.parse("2026-07-13T00:00:00Z");
        FundEntity fund = fund(1L);
        FundTransactionEntity tx = tx(10L, fund, FundTransactionSource.INCREASE);
        tx.setAmount(new BigDecimal("1000"));
        tx.setCreatedDate(Instant.parse("2026-07-10T06:55:00Z")); // 北京时间周五 14:55

        when(fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING))
                .thenReturn(List.of(tx));
        when(fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetween(
                1L, friday, friday.plus(1, ChronoUnit.DAYS)))
                .thenReturn(List.of(nav(fund, "1.25")));

        int confirmed = service.confirmPendingTransactions(monday);

        assertThat(confirmed).isEqualTo(1);
        assertThat(tx.getStatus()).isEqualTo(FundTransactionStatus.CONFIRMED);
        verify(transactionConfirmSupport).onBuyConfirmed(tx, new BigDecimal("1.25"));
    }

    private FundEntity fund(Long id) {
        FundEntity fund = new FundEntity();
        fund.setId(id);
        return fund;
    }

    private FundTransactionEntity tx(Long id, FundEntity fund, FundTransactionSource source) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setId(id);
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setStatus(FundTransactionStatus.PENDING);
        return tx;
    }

    private FundNavHistoryEntity nav(FundEntity fund, String value) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setAccumulatedNav(new BigDecimal(value));
        return nav;
    }
}
