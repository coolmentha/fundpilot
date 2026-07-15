package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.user.service.UserConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DcaBudgetSummaryServiceUnitTest {

    private static final Instant NOW = Instant.parse("2026-07-13T06:54:00Z");
    private static final Instant MONTH_START = Instant.parse("2026-06-30T16:00:00Z");
    private static final Instant MONTH_END = Instant.parse("2026-07-31T16:00:00Z");
    @Mock
    UserConfigService userConfigService;

    @Mock
    FundTransactionRepository fundTransactionRepository;

    @Mock
    DcaScheduleService dcaScheduleService;

    @Mock
    DcaPlanForecastService dcaPlanForecastService;

    private DcaBudgetSummaryService service;

    @BeforeEach
    void setUp() {
        service = new DcaBudgetSummaryService(
                userConfigService,
                fundTransactionRepository,
                dcaScheduleService,
                dcaPlanForecastService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(dcaScheduleService.startOfCurrentMonth(NOW)).thenReturn(MONTH_START);
        when(dcaScheduleService.startOfNextMonth(NOW)).thenReturn(MONTH_END);
    }

    @Test
    void currentMonth_按非取消Invest总额计算超额() {
        when(fundTransactionRepository.sumAmountBySourceAndStatusNotAndTradeDateBetween(
                FundTransactionSource.INVEST, FundTransactionStatus.CANCELLED, MONTH_START, MONTH_END))
                .thenReturn(new BigDecimal("650"));
        when(dcaPlanForecastService.currentMonthRemainingAmount()).thenReturn(BigDecimal.ZERO);
        when(userConfigService.getMonthlyDcaBudget()).thenReturn(new BigDecimal("500"));

        var view = service.currentMonth();

        assertThat(view.investedAmount()).isEqualByComparingTo("650");
        assertThat(view.projectedAmount()).isEqualByComparingTo("650");
        assertThat(view.remainingAmount()).isEqualByComparingTo("0");
        assertThat(view.overBudgetAmount()).isEqualByComparingTo("150");
        verify(fundTransactionRepository).sumAmountBySourceAndStatusNotAndTradeDateBetween(
                FundTransactionSource.INVEST, FundTransactionStatus.CANCELLED, MONTH_START, MONTH_END);
    }

    @Test
    void currentMonth_预算为空时保留金额且不生成剩余超额() {
        when(fundTransactionRepository.sumAmountBySourceAndStatusNotAndTradeDateBetween(
                FundTransactionSource.INVEST, FundTransactionStatus.CANCELLED, MONTH_START, MONTH_END))
                .thenReturn(null);
        when(dcaPlanForecastService.currentMonthRemainingAmount()).thenReturn(BigDecimal.ZERO);
        when(userConfigService.getMonthlyDcaBudget()).thenReturn(null);

        var view = service.currentMonth();

        assertThat(view.investedAmount()).isEqualByComparingTo("0");
        assertThat(view.futureAmount()).isEqualByComparingTo("0");
        assertThat(view.remainingAmount()).isNull();
        assertThat(view.overBudgetAmount()).isNull();
    }

}
