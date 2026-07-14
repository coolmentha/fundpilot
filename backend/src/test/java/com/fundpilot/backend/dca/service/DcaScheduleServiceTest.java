package com.fundpilot.backend.dca.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.dca.entity.FundDcaPlanEntity;
import com.fundpilot.backend.dca.enums.DcaFrequency;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DcaScheduleServiceTest {

    @Mock
    TradingCalendarService tradingCalendarService;

    private DcaScheduleService dcaScheduleService;

    @BeforeEach
    void setUp() {
        dcaScheduleService = new DcaScheduleService(tradingCalendarService);
    }

    @Test
    void isFutureExecutionDay_当天14点55分前仍算未来_到点后不再预测() {
        FundDcaPlanEntity plan = plan(DcaFrequency.DAILY);
        Instant candidate = Instant.parse("2026-07-13T00:00:00Z");
        when(tradingCalendarService.isTradingDay(ChinaTradingDate.toUtcDate(candidate))).thenReturn(true);

        assertThat(dcaScheduleService.isFutureExecutionDay(
                plan, candidate, Instant.parse("2026-07-13T06:54:59Z"))).isTrue();
        assertThat(dcaScheduleService.isFutureExecutionDay(
                plan, candidate, Instant.parse("2026-07-13T06:55:00Z"))).isFalse();
    }

    @Test
    void isFutureExecutionDay_月计划连续休市后按实际跨月日期执行() {
        FundDcaPlanEntity plan = plan(DcaFrequency.MONTHLY);
        plan.setDayOfMonth(28);
        Instant june28 = Instant.parse("2026-06-28T00:00:00Z");
        Instant june29 = Instant.parse("2026-06-29T00:00:00Z");
        Instant june30 = Instant.parse("2026-06-30T00:00:00Z");
        Instant july1 = Instant.parse("2026-07-01T00:00:00Z");
        when(tradingCalendarService.isTradingDay(june28)).thenReturn(false);
        when(tradingCalendarService.isTradingDay(june29)).thenReturn(false);
        when(tradingCalendarService.isTradingDay(june30)).thenReturn(false);
        when(tradingCalendarService.isTradingDay(july1)).thenReturn(true);

        assertThat(dcaScheduleService.isFutureExecutionDay(
                plan, july1, Instant.parse("2026-07-01T06:00:00Z"))).isTrue();
    }

    private static FundDcaPlanEntity plan(DcaFrequency frequency) {
        FundDcaPlanEntity plan = new FundDcaPlanEntity();
        plan.setFrequency(frequency);
        return plan;
    }
}
