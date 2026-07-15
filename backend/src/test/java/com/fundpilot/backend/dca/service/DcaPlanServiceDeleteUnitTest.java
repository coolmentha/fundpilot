package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DcaPlanServiceDeleteUnitTest {

    @Mock
    FundDcaPlanRepository planRepository;

    @Mock
    FundRepository fundRepository;

    @Mock
    DcaPlanForecastService forecastService;

    private DcaPlanService service;

    @BeforeEach
    void setUp() {
        service = new DcaPlanService(planRepository, fundRepository, forecastService);
    }

    @Test
    void delete_DRAFT_调用软删除仓储() {
        FundDcaPlanEntity plan = plan(DcaPlanStatus.DRAFT);
        when(planRepository.findById(7L)).thenReturn(Optional.of(plan));

        service.delete(7L);

        verify(planRepository).delete(plan);
    }

    @Test
    void delete_EFFECTIVE_返回专用错误码且不删除() {
        FundDcaPlanEntity plan = plan(DcaPlanStatus.EFFECTIVE);
        when(planRepository.findById(7L)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DCA_PLAN_DELETE_REQUIRES_DRAFT.name());
        verify(planRepository, never()).delete(plan);
    }

    @Test
    void delete_不存在计划_返回_NOT_FOUND() {
        when(planRepository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.DCA_PLAN_NOT_FOUND.name());
    }

    private static FundDcaPlanEntity plan(DcaPlanStatus status) {
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setId(7L);
        plan.setStatus(status);
        return plan;
    }
}
