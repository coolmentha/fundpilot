package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.client.EastmoneyFundFeeClient;
import com.fundpilot.backend.fund.client.FundFeeSnapshot;
import com.fundpilot.backend.fund.controller.FundFeeView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundFeeEntity;
import com.fundpilot.backend.fund.repository.FundFeeRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundFeeServiceTest {

    @Test
    void refreshHoldingFunds_不得在只读事务中刷新费率() throws Exception {
        Transactional transactional = FundFeeService.class
                .getMethod("refreshHoldingFunds")
                .getAnnotation(Transactional.class);

        assertThat(transactional == null || !transactional.readOnly()).isTrue();
    }

    @Test
    void getFeeView_缓存缺失时即时爬取并返回新缓存() {
        EastmoneyFundFeeClient client = mock(EastmoneyFundFeeClient.class);
        FundFeeRepository feeRepository = mock(FundFeeRepository.class);
        FundRepository fundRepository = mock(FundRepository.class);
        FundFeeService service = spy(new FundFeeService(client, feeRepository, fundRepository));

        FundEntity fund = new FundEntity();
        fund.setFundCode("020608");

        FundFeeEntity cached = new FundFeeEntity();
        cached.setFundCode("020608");
        cached.setPurchaseRate(new BigDecimal("0.012"));
        cached.setDiscountRate(new BigDecimal("0.0012"));
        cached.setSalesServiceFee(BigDecimal.ZERO);

        when(fundRepository.findById(5L)).thenReturn(Optional.of(fund));
        when(feeRepository.findByFundCode("020608"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cached));
        doReturn(new FundFeeSnapshot(new BigDecimal("0.0012"), List.of(), BigDecimal.ZERO))
                .when(service).fetchAndSave("020608");

        FundFeeView view = service.getFeeView(5L);

        assertThat(view).isNotNull();
        assertThat(view.purchaseRate()).isEqualByComparingTo(new BigDecimal("0.012"));
        assertThat(view.discountRate()).isEqualByComparingTo(new BigDecimal("0.0012"));
        verify(service).fetchAndSave("020608");
    }
}
