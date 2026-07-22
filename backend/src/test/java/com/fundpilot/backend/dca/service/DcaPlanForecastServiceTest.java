package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.dca.enums.DcaPlanStatus;
import com.fundpilot.backend.dca.repository.FundDcaPlanRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DcaPlanForecastServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:54:00Z");
    private static final Instant DAY_START = Instant.parse("2026-07-12T16:00:00Z");
    private static final Instant MONTH_END = Instant.parse("2026-07-31T16:00:00Z");

    @Mock
    FundDcaPlanRepository fundDcaPlanRepository;

    @Mock
    FundTransactionRepository fundTransactionRepository;

    @Mock
    DcaScheduleService dcaScheduleService;

    @Mock
    FundAccessService fundAccessService;

    private DcaPlanForecastService service;

    @BeforeEach
    void setUp() {
        service = new DcaPlanForecastService(
                fundDcaPlanRepository,
                fundTransactionRepository,
                fundAccessService,
                dcaScheduleService,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void currentMonthRemainingAmount_使用管理页可见计划集合且任意状态交易都占用本期日期() {
        FundDcaPlanEntity plan = activePlan(7L, "200");
        FundTransactionRepository.DcaTransactionDateProjection occurrence =
                mock(FundTransactionRepository.DcaTransactionDateProjection.class);
        when(occurrence.getDcaPlanId()).thenReturn(7L);
        when(occurrence.getTradeDate()).thenReturn(Instant.parse("2026-07-13T06:55:00Z"));
        when(fundDcaPlanRepository.findAllWithFund()).thenReturn(List.of(plan));
        when(fundAccessService.isOwned(plan.getFundEntity())).thenReturn(true);
        when(dcaScheduleService.startOfBusinessDay(NOW)).thenReturn(DAY_START);
        when(dcaScheduleService.startOfNextMonth(NOW)).thenReturn(Instant.parse("2026-07-13T16:00:00Z"));
        when(fundTransactionRepository.findDcaTransactionDates(
                List.of(7L), DAY_START, Instant.parse("2026-07-13T16:00:00Z")))
                .thenReturn(List.of(occurrence));

        assertThat(service.currentMonthRemainingAmount()).isEqualByComparingTo("0");
        verify(fundDcaPlanRepository).findAllWithFund();
        verify(dcaScheduleService, never()).isFutureExecutionDay(any(), any(), any());
    }

    @Test
    void currentMonthExecutionDates_返回逐计划日期并过滤停用计划() {
        FundDcaPlanEntity active = activePlan(7L, "200");
        FundDcaPlanEntity draft = activePlan(8L, "500");
        draft.setStatus(DcaPlanStatus.DRAFT);
        Instant nextDay = Instant.parse("2026-07-13T16:00:00Z");
        when(dcaScheduleService.startOfBusinessDay(NOW)).thenReturn(DAY_START);
        when(dcaScheduleService.startOfNextMonth(NOW)).thenReturn(MONTH_END);
        when(fundTransactionRepository.findDcaTransactionDates(List.of(7L), DAY_START, MONTH_END))
                .thenReturn(List.of());
        when(dcaScheduleService.isFutureExecutionDay(active, DAY_START, NOW)).thenReturn(true);
        when(dcaScheduleService.isFutureExecutionDay(active, nextDay, NOW)).thenReturn(true);

        var dates = service.currentMonthExecutionDates(List.of(active, draft));

        assertThat(dates.get(7L)).containsExactly(
                Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-14T00:00:00Z"));
        assertThat(dates).doesNotContainKey(8L);
    }

    private static FundDcaPlanEntity activePlan(Long id, String amount) {
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setId(id);
        plan.setEnabled(true);
        plan.setStatus(DcaPlanStatus.EFFECTIVE);
        plan.setAmount(new BigDecimal(amount));
        plan.setFrequency(DcaFrequency.DAILY);
        return plan;
    }
}
