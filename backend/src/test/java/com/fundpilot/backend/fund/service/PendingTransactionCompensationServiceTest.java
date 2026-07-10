package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingTransactionCompensationServiceTest {

    @Mock private FundTransactionRepository fundTransactionRepository;
    @Mock private NavConfirmService navConfirmService;
    @InjectMocks private PendingTransactionCompensationService service;

    @Test
    void compensateAll_单只基金失败不阻断其他基金() {
        when(fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING))
                .thenReturn(List.of(transaction(1L), transaction(2L), transaction(1L)));
        when(navConfirmService.confirmPendingTransactionsForFund(1L))
                .thenThrow(new IllegalStateException("bad lot"));
        when(navConfirmService.confirmPendingTransactionsForFund(2L)).thenReturn(1);

        int confirmed = service.compensateAll();

        assertThat(confirmed).isEqualTo(1);
        verify(navConfirmService).confirmPendingTransactionsForFund(1L);
        verify(navConfirmService).confirmPendingTransactionsForFund(2L);
    }

    private FundTransactionEntity transaction(Long fundId) {
        FundEntity fund = new FundEntity();
        fund.setId(fundId);
        FundTransactionEntity transaction = new FundTransactionEntity();
        transaction.setFundEntity(fund);
        return transaction;
    }
}
