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
import com.fundpilot.backend.strategy.service.TakeProfitLifecycleService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionConfirmServiceStateTest {

    @Mock private FundTransactionRepository fundTransactionRepository;
    @Mock private FundNavHistoryRepository fundNavHistoryRepository;
    @Mock private TransactionConfirmSupport transactionConfirmSupport;
    @Mock private FundPositionService fundPositionService;
    @Mock private TakeProfitLifecycleService takeProfitLifecycleService;
    @InjectMocks private TransactionConfirmService service;

    @Test
    void confirm_转出已确认时只确认待处理转入腿() {
        FundEntity fundA = fund(1L);
        FundEntity fundB = fund(2L);
        FundTransactionEntity out = tx(10L, fundA, FundTransactionSource.TRANSFER_OUT,
                FundTransactionStatus.CONFIRMED);
        out.setShares(new BigDecimal("500"));
        out.setAmount(new BigDecimal("600"));
        FundTransactionEntity in = tx(11L, fundB, FundTransactionSource.TRANSFER_IN,
                FundTransactionStatus.PENDING);
        out.setRelatedFundTransactionEntity(in);
        in.setRelatedFundTransactionEntity(out);

        when(fundTransactionRepository.findById(11L)).thenReturn(Optional.of(in));
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(2L))
                .thenReturn(List.of(nav(fundB, "2.00")));

        List<FundTransactionEntity> confirmed = service.confirm(11L);

        assertThat(confirmed).containsExactly(in);
        assertThat(out.getAmount()).isEqualByComparingTo("600");
        verify(transactionConfirmSupport, never()).onSellConfirmed(any(), any());
        verify(takeProfitLifecycleService).onTransactionConfirmed(in);
        verify(fundPositionService).reconcileStatus(2L);
        verify(fundPositionService, never()).reconcileStatus(1L);
    }

    private FundEntity fund(Long id) {
        FundEntity fund = new FundEntity();
        fund.setId(id);
        return fund;
    }

    private FundTransactionEntity tx(Long id, FundEntity fund, FundTransactionSource source,
                                     FundTransactionStatus status) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setId(id);
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setStatus(status);
        return tx;
    }

    private FundNavHistoryEntity nav(FundEntity fund, String value) {
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setFundEntity(fund);
        nav.setAccumulatedNav(new BigDecimal(value));
        return nav;
    }
}
