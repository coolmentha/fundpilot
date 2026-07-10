package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionCancelServiceStateTest {

    @Mock private FundTransactionRepository fundTransactionRepository;
    @InjectMocks private TransactionCancelService service;

    @Test
    void cancel_关联转换腿已确认时拒绝半撤销() {
        FundTransactionEntity out = tx(10L, FundTransactionSource.TRANSFER_OUT,
                FundTransactionStatus.CONFIRMED);
        FundTransactionEntity in = tx(11L, FundTransactionSource.TRANSFER_IN,
                FundTransactionStatus.PENDING);
        out.setRelatedFundTransactionEntity(in);
        in.setRelatedFundTransactionEntity(out);
        when(fundTransactionRepository.findById(11L)).thenReturn(Optional.of(in));

        assertThatThrownBy(() -> service.cancel(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关联");

        assertThat(in.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        verify(fundTransactionRepository, never()).save(in);
    }

    private FundTransactionEntity tx(Long id, FundTransactionSource source, FundTransactionStatus status) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setId(id);
        tx.setSource(source);
        tx.setStatus(status);
        return tx;
    }
}
