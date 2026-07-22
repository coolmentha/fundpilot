package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.client.FundFeeSnapshot;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundTransactionPendingViewTest {

    @Mock FundTransactionRepository transactionRepository;
    @Mock FundRepository fundRepository;
    @Mock FundNavHistoryRepository navRepository;
    @Mock TransactionConfirmSupport confirmSupport;
    @Mock FundPositionService positionService;
    @Mock FundFeeService feeService;
    @Mock FundAccessService fundAccessService;

    @Test
    void 待确认买入仅在交易日净值入库后返回预计份额() {
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setId(2L);
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setAmount(new BigDecimal("1000"));
        tx.setTradeDate(Instant.parse("2026-07-17T00:00:00Z"));
        FundNavHistoryEntity nav = new FundNavHistoryEntity();
        nav.setNav(new BigDecimal("2"));
        when(transactionRepository.findByStatusOrderByTradeDateDesc(FundTransactionStatus.PENDING))
                .thenReturn(List.of(tx));
        when(fundAccessService.isOwned(fund)).thenReturn(true);
        when(navRepository.findByFundEntity_IdAndNavDateGreaterThanEqualAndNavDateLessThan(
                eq(1L), any(), any())).thenReturn(List.of(nav));
        when(feeService.getFeeByFundId(1L)).thenReturn(
                new FundFeeSnapshot(new BigDecimal("0.0015"), List.of(), null));
        FundTransactionService service = new FundTransactionService(fundAccessService, transactionRepository, fundRepository,
                navRepository, feeService, confirmSupport, positionService);

        FundTransactionView view = service.listPending().getFirst();

        assertThat(view.confirmationState()).isEqualTo("READY");
        assertThat(view.expectedShares()).isEqualByComparingTo("499.25");
    }
}
